package net.derfla.quickeconomy.util;

import net.derfla.quickeconomy.Main;
import org.bukkit.plugin.Plugin;

import java.sql.*;

public class DatabaseManager {

    static Plugin plugin = Main.getInstance();
    public static Connection connection;

    public static Connection getConnection() {
        return connection;
    }

    public static void connectToDatabase() throws SQLException {
        String type = plugin.getConfig().getString("database.type");
        if ("mysql".equalsIgnoreCase(type)) {
            String host = plugin.getConfig().getString("database.host");
            int port = plugin.getConfig().getInt("database.port");
            String database = plugin.getConfig().getString("database.database");
            String user = plugin.getConfig().getString("database.username");
            String password = plugin.getConfig().getString("database.password");

            String url = "jdbc:mysql://" + host + ":" + port + "/" + database;
            connection = DriverManager.getConnection(url, user, password);
        } else if ("sqlite".equalsIgnoreCase(type)) {
            String filePath = plugin.getConfig().getString("database.file");
            String url = "jdbc:sqlite:" + filePath;
            connection = DriverManager.getConnection(url);
        }

        plugin.getLogger().info("Database connection established.");
    }

        public static void createTables() {
        try (Statement statement = connection.createStatement()) {
            // Create PlayerAccounts table
            String sqlPlayerAccounts = "CREATE TABLE IF NOT EXISTS PlayerAccounts ("
                    + "  UUID char(32) NOT NULL,"
                    + "  AccountCreationDate DATETIME NOT NULL,"
                    + "  PlayerName varchar(16) NOT NULL,"
                    + "  Balance float NOT NULL DEFAULT 0,"
                    + "  PRIMARY KEY (UUID)"
                    + ");";
            statement.executeUpdate(sqlPlayerAccounts);

            String sqlTransactions = "CREATE TABLE IF NOT EXISTS Transactions ("
                    + "  TransactionID DATETIME NOT NULL,"
                    + "  TransactionType varchar(16) NOT NULL,"
                    + "  Induce varchar(16) NOT NULL,"
                    + "  Source char(32),"
                    + "  Destination char(32),"
                    + "  NewSourceBalance float,"
                    + "  NewDestinationBalance float,"
                    + "  Amount float NOT NULL,"
                    + "  Passed tinyint(1),"
                    + "  PassedReason varchar(16) DEFAULT NULL,"
                    + "  TransactionMessage varchar(32),"
                    + "  PRIMARY KEY (TransactionID),"
                    + "  FOREIGN KEY (Source) REFERENCES PlayerAccounts(UUID),"
                    + "  FOREIGN KEY (Destination) REFERENCES PlayerAccounts(UUID)"
                    + ");";
            statement.executeUpdate(sqlTransactions);

            String sqlAutopays = "CREATE TABLE IF NOT EXISTS Autopays ("
                    + "  AutopayID int NOT NULL AUTO_INCREMENT,"
                    + "  Active tinyint(1) NOT NULL DEFAULT 1,"
                    + "  CreationDate DATETIME NOT NULL,"
                    + "  AutopayName varchar(16),"
                    + "  Source char(32),"
                    + "  Destination char(32),"
                    + "  Amount float NOT NULL,"
                    + "  InverseFrequency int NOT NULL,"
                    + "  EndsAfter int NOT NULL,"
                    + "  PRIMARY KEY (AutopayID),"
                    + "  FOREIGN KEY (Source) REFERENCES PlayerAccounts(UUID),"
                    + "  FOREIGN KEY (Destination) REFERENCES PlayerAccounts(UUID)"
                    + ");";
            statement.executeUpdate(sqlAutopays);

            plugin.getLogger().info("Tables created or already exist.");
        } catch (SQLException e) {
            plugin.getLogger().severe("Error creating tables: " + e.getMessage());
        }
    }

    public static void addAccount(String uuid, String playerName) {

        String sql = "INSERT INTO PlayerAccounts (UUID, AccountCreationDate, PlayerName, Balance) " +
                "VALUES (?, CURRENT_DATE, ?, 0) " +
                "ON DUPLICATE KEY UPDATE PlayerName = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, uuid);
            pstmt.setString(2, playerName);
            pstmt.setString(3, uuid);

            int rowsAffected = pstmt.executeUpdate();

            if (rowsAffected > 0) {
                plugin.getLogger().info(rowsAffected == 1 ? "New player account added successfully for " + playerName :
                        "Player account updated for " + playerName);
            } else {
                plugin.getLogger().info("No changes made for player account: " + playerName);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error adding/updating player account: " + e.getMessage());
        }
    }

    public static void executeTransaction(String transactType, String induce, String source, String destination, int amount, String transactionMessage) {
        String insertTransactionSQL = "INSERT INTO Transactions (TransactionID, TransactionType, Induce, Source, Destination, Amount, TransactionMessage) VALUES (?, ?, ?, ?, ?, ?, ?)";
        String updateSourceSQL = "UPDATE PlayerAccounts SET Balance = Balance - ? WHERE UUID = ? AND Balance >= ?";
        String updateDestinationSQL = "UPDATE PlayerAccounts SET Balance = Balance + ? WHERE UUID = ?";
        String insertFailedTransactionSQL = "INSERT INTO FailedTransactions (TransactionID, TransactionType, Induce, Source, Destination, Amount, Reason) VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement insertTransaction = conn.prepareStatement(insertTransactionSQL);
             PreparedStatement updateSource = conn.prepareStatement(updateSourceSQL);
             PreparedStatement updateDestination = conn.prepareStatement(updateDestinationSQL);
             PreparedStatement insertFailedTransaction = conn.prepareStatement(insertFailedTransactionSQL)) {

            conn.setAutoCommit(false);

            // Insert transaction
            insertTransaction.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
            insertTransaction.setString(2, transactType);
            insertTransaction.setString(3, induce);
            insertTransaction.setString(4, source);
            insertTransaction.setString(5, destination);
            insertTransaction.setInt(6, amount);
            insertTransaction.setString(7, transactionMessage);
            insertTransaction.executeUpdate();

            // Update source account if applicable
            if (source != null) {
                updateSource.setInt(1, amount);
                updateSource.setString(2, source);
                updateSource.setInt(3, amount);
                int rowsAffected = updateSource.executeUpdate();
                if (rowsAffected == 0) {
                    throw new SQLException("Insufficient funds in the source account");
                }
            }

            // Update destination account if applicable
            if (destination != null) {
                updateDestination.setInt(1, amount);
                updateDestination.setString(2, destination);
                updateDestination.executeUpdate();
            }

            conn.commit();
            plugin.getLogger().info("Transaction completed successfully");

        } catch (SQLException e) {
            plugin.getLogger().severe("Error executing transaction: " + e.getMessage());
            try (Connection conn = getConnection();
                 PreparedStatement insertFailedTransaction = conn.prepareStatement(insertFailedTransactionSQL)) {

                insertFailedTransaction.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
                insertFailedTransaction.setString(2, transactType);
                insertFailedTransaction.setString(3, induce);
                insertFailedTransaction.setString(4, source);
                insertFailedTransaction.setString(5, destination);
                insertFailedTransaction.setInt(6, amount);
                insertFailedTransaction.setString(7, e.getMessage().contains("Insufficient funds") ? "low_balance" : "unexpected_error");
                insertFailedTransaction.executeUpdate();

                plugin.getLogger().info("Failed transaction recorded");
            } catch (SQLException ex) {
                plugin.getLogger().severe("Error recording failed transaction: " + ex.getMessage());
            }
        }
    }
}
