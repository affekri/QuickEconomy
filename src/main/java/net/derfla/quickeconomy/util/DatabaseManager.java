package net.derfla.quickeconomy.util;

import net.derfla.quickeconomy.Main;
import org.bukkit.plugin.Plugin;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        String trimmedUuid = TypeChecker.trimUUID(uuid);
        String sql = "INSERT INTO PlayerAccounts (UUID, AccountCreationDate, PlayerName) "
                   + "VALUES (?, GETDATE(), ?) "
                   + "ON DUPLICATE KEY UPDATE PlayerName = VALUES(PlayerName);";
    
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, trimmedUuid);
            pstmt.setString(2, playerName);
            int rowsAffected = pstmt.executeUpdate();
            
            if (rowsAffected > 0) {
                plugin.getLogger().info("New player account added successfully for " + playerName);
            } else {
                plugin.getLogger().info("Account already exists for " + playerName);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error adding player account: " + e.getMessage());
        }
        createTransactionsView(trimmedUuid);
    }

    public static void createTransactionsView(String uuid) {
        try (Statement statement = connection.createStatement()) {
            String trimmedUuid = TypeChecker.trimUUID(uuid);
            String viewName = "vw_Transactions_" + trimmedUuid;
            String sql = "CREATE VIEW IF NOT EXISTS" + viewName + " AS "
                    + "SELECT "
                    + "    CAST(t.TransactionID AS TIMESTAMP) AS TransactionDateTime, "
                    + "    CASE "
                    + "        WHEN t.Source = '" + trimmedUuid + "' THEN SUM(0 - t.Amount)"
                    + "        ELSE t.Amount"
                    + "    END AS Amount,"
                    + "    pa.PlayerName, "
                    + "    CASE "
                    + "        WHEN t.Passed = 1 THEN 'Passed' "
                    + "        WHEN t.Passed = 0 THEN 'Failed' "
                    + "        ELSE 'Unknown' "
                    + "    END AS Passed, "
                    + "    CASE WHEN t.Passed != 1 THEN t.PassedReason ELSE NULL END AS PassedReason, "
                    + "    CASE "
                    + "        WHEN t.Source != '" + trimmedUuid + "' THEN "
                    + "            CASE "
                    + "                WHEN t.TransactionType = 'autopay' THEN CONCAT('Autopay #', t.Induce) "
                    + "                WHEN t.Induce = 'command' THEN COALESCE(NULLIF(t.TransactionMessage, ''), '-') "
                    + "                ELSE '-' "
                    + "            END "
                    + "        ELSE "
                    + "            CASE "
                    + "                WHEN t.TransactionType = 'insert' THEN 'Bank deposit' "
                    + "                WHEN t.TransactionType = 'withdraw' THEN 'Bank withdrawal' "
                    + "                WHEN t.TransactionType = 'autopay' THEN CONCAT('Autopay #', t.Induce) "
                    + "                WHEN t.TransactionType = 'p2p' THEN CASE "
                    + "                    WHEN t.Induce REGEXP '^[0-9]+$' THEN CONCAT('Autopay #', t.Induce) "
                    + "                    WHEN t.Induce = 'command' THEN 'Command' "
                    + "                    WHEN t.Induce = 'purchase' THEN 'Purchase' "
                    + "                    ELSE 'Unknown' "
                    + "                END "
                    + "                WHEN t.TransactionType = 'system' THEN CASE "
                    + "                    WHEN t.Induce = 'admin_command' THEN 'Admin command' "
                    + "                    ELSE 'Unknown' "
                    + "                END "
                    + "                ELSE 'Unknown' "
                    + "            END "
                    + "    END AS Reason "
                    + "FROM Transactions t "
                    + "LEFT JOIN PlayerAccounts pa ON t.Destination = pa.UUID "
                    + "WHERE t.Source = '" + trimmedUuid + "' OR t.Destination = '" + trimmedUuid + "';";
            
            statement.executeUpdate(sql);
            plugin.getLogger().info("Transaction view created for UUID: " + uuid);
        } catch (SQLException e) {
            plugin.getLogger().severe("Error creating transaction view: " + e.getMessage());
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

    public static double displayBalance(String uuid) {
        String trimmedUuid = TypeChecker.trimUUID(uuid);
        String sql = "SELECT Balance FROM PlayerAccounts WHERE UUID = ?";
        double balance = 0.0;
    
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, trimmedUuid);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    balance = rs.getDouble("Balance");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error viewing balance: " + e.getMessage());
        }
    
        return balance;
    }

    public static List<Map<String, Object>> displayTransactionsView(String uuid) {
        String trimmedUuid = TypeChecker.trimUUID(uuid);
        String viewName = "vw_Transactions_" + trimmedUuid;
        String sql = "SELECT * FROM " + viewName + " ORDER BY TransactionDateTime DESC";
        List<Map<String, Object>> transactions = new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            while (rs.next()) {
                Map<String, Object> transaction = new HashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    String columnName = metaData.getColumnName(i);
                    Object value = rs.getObject(i);
                    transaction.put(columnName, value);
                }
                transactions.add(transaction);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error viewing transactions for UUID " + uuid + ": " + e.getMessage());
        }

        return transactions;
    }

    public static void addAutopay(String autopayName, String uuid, String destination,
                                   double amount, int inverseFrequency, int endsAfter) {
        String trimmedUuid = TypeChecker.trimUUID(uuid);
        String trimmedDestination = TypeChecker.trimUUID(destination);

        if (amount < 0) {
            plugin.getLogger().severe("Error: Amount must be greater than 0.");
            return;
        }
        if (inverseFrequency < 0) {
            plugin.getLogger().severe("Error: InverseFrequency must be greater than 0.");
            return;
        }
        if (endsAfter <= 0) {
            plugin.getLogger().severe("Error: EndsAfter must be 0 (for continuous) or greater.");
            return;
        }

        String sql = "DECLARE @AutopayName varchar(16) = ?;"
                + "DECLARE @UUID char(32) = ?;"
                + "DECLARE @Destination char(32) = ?;"
                + "DECLARE @Amount float = ?;"
                + "DECLARE @InverseFrequency int NOT NULL = ?;"
                + "DECLARE @EndsAfter int NOT NULL = ?;"
                + "BEGIN TRY"
                + "    INSERT INTO Autopays ("
                + "        Active, CreationDate, AutopayName, Source, Destination,"
                + "        Amount, InverseFrequency, EndsAfter"
                + "    )"
                + "    VALUES ("
                + "        1, GETDATE(), @AutopayName, @UUID, @Destination,"
                + "        @Amount, @InverseFrequency, @EndsAfter"
                + "    );"
                + "    PRINT 'Autopay created successfully.';"
                + "END TRY"
                + "BEGIN CATCH"
                + "    PRINT 'Error occurred while creating autopay:';"
                + "    PRINT ERROR_MESSAGE();"
                + "END CATCH;";

        try (Connection conn = getConnection();
            PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, autopayName);
            pstmt.setString(2, trimmedUuid);
            pstmt.setString(3, trimmedDestination);
            pstmt.setDouble(4, amount);
            pstmt.setInt(5, inverseFrequency);
            pstmt.setInt(6, endsAfter);

            pstmt.executeUpdate();
            plugin.getLogger().info("Autopay added successfully");
        } catch (SQLException e) {
            plugin.getLogger().severe("Error adding autopay: " + e.getMessage());
        }
    }

    public static void stateChangeAutopay(boolean activeState, int autopayID, String uuid) {
        String trimmedUuid = TypeChecker.trimUUID(uuid);
        String sql = "UPDATE Autopays SET Active = ? WHERE AutopayID = ? AND Source = ?;";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setBoolean(1, activeState);
            pstmt.setInt(2, autopayID);
            pstmt.setString(3, trimmedUuid);

            int rowsAffected = pstmt.executeUpdate();
            if (rowsAffected > 0) {
                plugin.getLogger().info("Autopay updated successfully");
            } else {
                plugin.getLogger().info("Autopay not found. No update was performed.");
            }
        } catch (SQLException e) {
            String action = activeState ? "activating" : "deactivating";
            plugin.getLogger().severe("Error " + action + " autopay: " + e.getMessage());
        }
    }

    public static void deleteAutopay(int autopayID, String uuid) {
        String trimmedUuid = TypeChecker.trimUUID(uuid);
        String sql = "DELETE FROM Autopays WHERE AutopayID = ? AND Source = ?;";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, autopayID);
            pstmt.setString(2, trimmedUuid);

            int rowsAffected = pstmt.executeUpdate();
            if (rowsAffected > 0) {
                plugin.getLogger().info("Autopay deleted successfully");
            } else {
                plugin.getLogger().info("Autopay not found. No deletion was performed.");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error deleting autopay: " + e.getMessage());
        }
    }

    public static List<Map<String, Object>> viewAutopays(String uuid) {
        String trimmedUuid = TypeChecker.trimUUID(uuid);
        String sql = "SELECT * FROM Autopays WHERE Source = ? ORDER BY AutopayID";
        List<Map<String, Object>> autopays = new ArrayList<>();
    
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, trimmedUuid);
    
            try (ResultSet rs = pstmt.executeQuery()) {
                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();
    
                while (rs.next()) {
                    Map<String, Object> autopay = new HashMap<>();
                    for (int i = 1; i <= columnCount; i++) {
                        String columnName = metaData.getColumnName(i);
                        Object value = rs.getObject(i);
                        autopay.put(columnName, value);
                    }
                    autopays.add(autopay);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error viewing autopays for UUID " + uuid + ": " + e.getMessage());
        }
    
        return autopays;
    }

    public static List<Map<String, Object>> listAllAccounts() {
        String sql = "SELECT PlayerName, Balance, AccountCreationDate FROM PlayerAccounts ORDER BY AccountCreationDate";
        List<Map<String, Object>> accounts = new ArrayList<>();

        try (Connection conn = getConnection();
            PreparedStatement pstmt = conn.prepareStatement(sql);
            ResultSet rs = pstmt.executeQuery()) {

            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            while (rs.next()) {
                Map<String, Object> account = new HashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    String columnName = metaData.getColumnName(i);
                    Object value = rs.getObject(i);
                    account.put(columnName, value);
                }
                accounts.add(account);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error listing all accounts: " + e.getMessage());
        }

        return accounts;
    }

    public static void rollback(Timestamp rollbackTime, boolean keepTransactions) {
        String getBalances = "SELECT pa.UUID, pa.Balance, COALESCE(t.NewSourceBalance, t.NewDestinationBalance) AS RollbackBalance FROM PlayerAccounts pa LEFT JOIN (SELECT DISTINCT ON (Source) Source, NewSourceBalance FROM Transactions WHERE TransactionID <= ? ORDER BY Source, TransactionID DESC) t ON pa.UUID = t.Source";
        String deleteTransactions = "DELETE FROM Transactions WHERE TransactionID > ?";
        String getAutopays = "SELECT AutopayID, CreationDate, Source FROM Autopays";
        String deactivateAutopays = "UPDATE Autopays SET Active = 0 WHERE CreationDate <= ?";
        
        try (Connection conn = getConnection();
             PreparedStatement pstmtGet = conn.prepareStatement(getBalances);
             PreparedStatement pstmtDelete = conn.prepareStatement(deleteTransactions);
             PreparedStatement pstmtGetAutopays = conn.prepareStatement(getAutopays);
             PreparedStatement pstmtDeactivateAutopays = conn.prepareStatement(deactivateAutopays)) {
            
            conn.setAutoCommit(false);
            
            pstmtGet.setTimestamp(1, rollbackTime);
            ResultSet rs = pstmtGet.executeQuery();
            while (rs.next()) {
                String uuid = rs.getString("UUID");
                double currentBalance = rs.getDouble("Balance");
                double rollbackBalance = rs.getDouble("RollbackBalance");
                double difference = rollbackBalance - currentBalance;
                if (difference != 0) {
                    executeTransaction("system", "command", null, uuid, difference, "Rollback adjustment");
                }
            }
            
            if (!keepTransactions) {
                pstmtDelete.setTimestamp(1, rollbackTime);
                pstmtDelete.executeUpdate();
            }
            
            ResultSet rsAutopays = pstmtGetAutopays.executeQuery();
            while (rsAutopays.next()) {
                int autopayID = rsAutopays.getInt("AutopayID");
                Timestamp creationDate = rsAutopays.getTimestamp("CreationDate");
                String source = rsAutopays.getString("Source");
                if (creationDate.after(rollbackTime)) {
                    deleteAutopay(autopayID, source);
                }
            }
            pstmtDeactivateAutopays.setTimestamp(1, rollbackTime);
            pstmtDeactivateAutopays.executeUpdate();
            
            conn.commit();
            plugin.getLogger().info("Rollback to " + rollbackTime + " completed successfully.");
        } catch (SQLException e) {
            plugin.getLogger().severe("Error during rollback: " + e.getMessage());
            try {
                if (connection != null) connection.rollback();
            } catch (SQLException ex) {
                plugin.getLogger().severe("Error rolling back transaction: " + ex.getMessage());
            }
        }
    }
  
}
