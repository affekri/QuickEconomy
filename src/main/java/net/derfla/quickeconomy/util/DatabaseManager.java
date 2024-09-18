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

    public static void createTable() {
        try (Statement statement = connection.createStatement()) {
            String sql = "CREATE TABLE IF NOT EXISTS PlayerAccounts ("
                    + "  UUID CHAR(32) NOT NULL,"
                    + "  AccountCreationDate DATE NOT NULL,"
                    + "  PlayerName VARCHAR(16) NOT NULL,"
                    + "  Balance INT NOT NULL DEFAULT 0,"
                    + "  PRIMARY KEY (UUID)"
                    + ");"
                    + "CREATE TABLE IF NOT EXISTS Transactions ("
                    + "  TransactionID DATETIME NOT NULL,"
                    + "  TransactionType VARCHAR(255) NOT NULL,"
                    + "  Induce VARCHAR(16) NOT NULL,"
                    + "  Source CHAR(32),"
                    + "  Destination CHAR(32),"
                    + "  Amount INT NOT NULL,"
                    + "  TransactionMessage VARCHAR(32),"
                    + "  PRIMARY KEY (TransactionID),"
                    + "  FOREIGN KEY (Source) REFERENCES PlayerAccounts(UUID),"
                    + "  FOREIGN KEY (Destination) REFERENCES PlayerAccounts(UUID)"
                    + ");"
                    + "CREATE TABLE IF NOT EXISTS FailedTransactions ("
                    + "  TransactionID DATETIME NOT NULL,"
                    + "  TransactionType VARCHAR(255) NOT NULL,"
                    + "  Induce VARCHAR(16) NOT NULL,"
                    + "  Source CHAR(32),"
                    + "  Destination CHAR(32),"
                    + "  Amount INT NOT NULL,"
                    + "  Reason VARCHAR(32) NOT NULL,"
                    + "  PRIMARY KEY (TransactionID),"
                    + "  FOREIGN KEY (Source) REFERENCES PlayerAccounts(UUID),"
                    + "  FOREIGN KEY (Destination) REFERENCES PlayerAccounts(UUID)";
            statement.executeUpdate(sql);
            plugin.getLogger().info("Economy table created or already exists.");
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

    public static void executeTransaction(String transactType, String induce, String source,
                                          String destination, double amount, String transactionMessage) {
        String trimmedSource = TypeChecker.trimUUID(source);
        String trimmedDestination = TypeChecker.trimUUID(destination);
        String sql = "DECLARE @TransactionID DATETIME = GETDATE();"
                + "DECLARE @TransactType varchar(16) = ?;"
                + "DECLARE @Induce varchar(16) = ?;"
                + "DECLARE @Source char(32) = ?;"
                + "DECLARE @Destination char(32) = ?;"
                + "DECLARE @Amount float = ?;"
                + "DECLARE @TransactionMessage varchar(32) DEFAULT NULL = ?;"
                + "DECLARE @NewSourceBalance float = (SELECT Balance - @Amount FROM PlayerAccounts WHERE UUID = @Source);"
                + "DECLARE @NewDestinationBalance float = (SELECT Balance + @Amount FROM PlayerAccounts WHERE UUID = @Destination);"
                + "BEGIN TRY"
                + "    BEGIN TRANSACTION;"
                + "    IF @Source IS NOT NULL AND @NewSourceBalance < 0"
                + "    BEGIN"
                + "        THROW 50001, 'Insufficient funds in the source account', 1;"
                + "    END"
                + "    INSERT INTO Transactions (TransactionID, TransactionType, Induce, Source, Destination, Amount, TransactionMessage)"
                + "    VALUES (@TransactionID, @TransactType, @Induce, @Source, @Destination, @Amount, @TransactionMessage);"
                + "    IF @Source IS NOT NULL"
                + "    BEGIN"
                + "        UPDATE PlayerAccounts"
                + "        SET Balance = @NewSourceBalance"
                + "        WHERE UUID = @Source;"
                + "    END"
                + "    IF @Destination IS NOT NULL"
                + "    BEGIN"
                + "        UPDATE PlayerAccounts"
                + "        SET Balance = @NewDestinationBalance"
                + "        WHERE UUID = @Destination;"
                + "    END"
                + "    BEGIN "
                + "        UPDATE Transactions"
                + "        SET Passed = 1"
                + "        WHERE TransactionID = @TransactionID"
                + "    COMMIT;"
                + "END TRY"
                + "BEGIN CATCH"
                + "    ROLLBACK;"
                + "    DECLARE @Reason varchar(32)"
                + "    SET @Reason = CASE"
                + "       WHEN ERROR_NUMBER() = 50001 THEN 'low_source_balance' ELSE 'unexpected_error' END);"
                + "       END"
                + "    INSERT INTO Transactions (TransactionID, TransactionType, Induce, Source, Destination, Amount, TransactionMessage)"
                + "    VALUES (@TransactionID, @TransactType, @Induce, @Source, @Destination, @Amount, @TransactionMessage);"
                + "    THROW;"
                + "END CATCH;";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, transactType);
            pstmt.setString(2, induce);
            pstmt.setString(3, trimmedSource);
            pstmt.setString(4, trimmedDestination);
            pstmt.setDouble(5, amount);
            pstmt.setString(6, transactionMessage);

            pstmt.executeUpdate();
            plugin.getLogger().info("Transaction executed successfully");
        } catch (SQLException e) {
            plugin.getLogger().severe("Error executing transaction: " + e.getMessage());
        }
    }
    
}
