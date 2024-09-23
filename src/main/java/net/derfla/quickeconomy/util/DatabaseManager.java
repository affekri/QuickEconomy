package net.derfla.quickeconomy.util;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.derfla.quickeconomy.Main;
import net.derfla.quickeconomy.file.BalanceFile;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.sql.*;
import java.util.*;
import java.io.FileWriter;
import java.io.IOException;

public class DatabaseManager {

    static Plugin plugin = Main.getInstance();
    private static HikariDataSource dataSource;

    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection(); // Get a connection from the pool
    }


    public static void connectToDatabase() {
        HikariConfig config = new HikariConfig();

        String type = plugin.getConfig().getString("database.type");
        if ("mysql".equalsIgnoreCase(type)) {
            String host = plugin.getConfig().getString("database.host");
            int port = plugin.getConfig().getInt("database.port");
            String database = plugin.getConfig().getString("database.database");
            String user = plugin.getConfig().getString("database.username");
            String password = plugin.getConfig().getString("database.password");

            String url = "jdbc:mysql://" + host + ":" + port + "/" + database;

            config.setJdbcUrl(url);
            config.setUsername(user);
            config.setPassword(password);

            // Optional HikariCP settings
            config.setMaximumPoolSize(10); // Max number of connections in the pool
            config.setConnectionTimeout(30000); // 30 seconds timeout for getting a connection
            config.setIdleTimeout(600000); // 10 minutes before an idle connection is closed
            config.setMaxLifetime(1800000); // 30 minutes max lifetime for a connection
        } else if ("sqlite".equalsIgnoreCase(type)) {
            String filePath = plugin.getConfig().getString("database.file");
            String url = "jdbc:sqlite:" + filePath;
            config.setJdbcUrl(url);
        }

        dataSource = new HikariDataSource(config);
        plugin.getLogger().info("Database connection pool established.");
    }

    public static void closePool() {
        if (dataSource != null) {
            dataSource.close(); // Close the pool when shutting down the plugin
            plugin.getLogger().info("Database connection pool closed.");
        }
    }

    public static void createTables() {
        try (Connection conn = getConnection();
             Statement statement = conn.createStatement()) {
            // Create PlayerAccounts table
            String sqlPlayerAccounts = "CREATE TABLE IF NOT EXISTS PlayerAccounts ("
                    + "  UUID char(32) NOT NULL,"
                    + "  AccountDatetime DATETIME NOT NULL,"
                    + "  PlayerName varchar(16) NOT NULL,"
                    + "  Balance float NOT NULL DEFAULT 0,"
                    + "  Change float NOT NULL DEFAULT 0"
                    + "  PRIMARY KEY (UUID)"
                    + ");";
            statement.executeUpdate(sqlPlayerAccounts);

            String sqlTransactions = "CREATE TABLE IF NOT EXISTS Transactions ("
                    + "  TransactionID bigint NOT NULL AUTO_INCREMENT"
                    + "  TransactionDatetime DATETIME NOT NULL,"
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
                    + "  AutopayID bigint NOT NULL AUTO_INCREMENT,"
                    + "  AutopayDatetime DATETIME NOT NULL"
                    + "  Active tinyint(1) NOT NULL DEFAULT 1,"
                    + "  AutopayName varchar(16),"
                    + "  Source char(32),"
                    + "  Destination char(32),"
                    + "  Amount float NOT NULL,"
                    + "  InverseFrequency int NOT NULL,"
                    + "  TimesLeft int,"
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

    public static void addAccount(@NotNull String uuid, @NotNull String playerName, double balance, double change) {
        String trimmedUuid = TypeChecker.trimUUID(uuid);
        Timestamp currentTime = new Timestamp(System.currentTimeMillis());

        if (accountExists(trimmedUuid)) {
            plugin.getLogger().info("Account already exists for player with UUID: " + trimmedUuid);
        } else {
            String insertSql = "INSERT INTO PlayerAccounts (UUID, AccountDatetime, PlayerName, Balance, Change) "
                    + "VALUES (?, ?, ?, ?, ?)";
            try (Connection conn = getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
                insertPstmt.setString(1, trimmedUuid);
                insertPstmt.setTimestamp(2, currentTime);
                insertPstmt.setString(3, playerName);
                insertPstmt.setDouble(4, balance);
                insertPstmt.setDouble(5, change);
                int rowsInserted = insertPstmt.executeUpdate();

                if (rowsInserted > 0) {
                    plugin.getLogger().info("New player account added successfully for " + playerName);
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Error adding player account: " + e.getMessage());
            }
        }

        createTransactionsView(trimmedUuid);
    }


    public static void executeTransaction(@NotNull String transactType, @NotNull String induce, String source,
                                          String destination, double amount, String transactionMessage) {
        String trimmedSource = TypeChecker.trimUUID(source);
        String trimmedDestination = TypeChecker.trimUUID(destination);
        Timestamp currentTime = new Timestamp(System.currentTimeMillis());
        String sql = "DECLARE @TransactionDatetime DATETIME = ?;"
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
                + "    IF @Source IS NOT NULL AND @NewSourceBalance <= 0"
                + "    BEGIN"
                + "        THROW 50001, 'Insufficient funds in the source account', 1;"
                + "    END"
                + "    INSERT INTO Transactions (TransactionDatetime, TransactionType, Induce, Source, Destination, Amount, TransactionMessage, Passed)"
                + "    VALUES (@TransactionDatetime, @TransactType, @Induce, @Source, @Destination, @Amount, @TransactionMessage, 1);"
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
                + "        WHERE TransactionID = LAST_INSERT_ID()"
                + "    COMMIT;"
                + "BEGIN CATCH"
                + "    ROLLBACK;"
                + "    DECLARE @ErrorMessage NVARCHAR(4000) = ERROR_MESSAGE();"
                + "    DECLARE @ErrorSeverity INT = ERROR_SEVERITY();"
                + "    DECLARE @ErrorState INT = ERROR_STATE();"
                + "    INSERT INTO Transactions (TransactionDatetime, TransactionType, Induce, Source, Destination, Amount, TransactionMessage, Passed, PassedReason)"
                + "    VALUES (@TransactionDatetime, @TransactType, @Induce, @Source, @Destination, @Amount, @TransactionMessage, 0, @ErrorMessage);"
                + "    WHERE TransactionID = LAST_INSERT_ID()"
                + "    RAISERROR(@ErrorMessage, @ErrorSeverity, @ErrorState);"
                + "END CATCH;";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setTimestamp(1, currentTime);
            pstmt.setString(2, transactType);
            pstmt.setString(3, induce);
            pstmt.setString(4, trimmedSource);
            pstmt.setString(5, trimmedDestination);
            pstmt.setDouble(6, amount);
            pstmt.setString(7, transactionMessage);

            pstmt.executeUpdate();
            plugin.getLogger().info("Transaction of " + amount + " executed successfully");
        } catch (SQLException e) {
            plugin.getLogger().severe("Error executing transaction: " + e.getMessage());
        }
    }

    private static void createTransactionsView(@NotNull String uuid) {
        String trimmedUuid = TypeChecker.trimUUID(uuid);
        String untrimmedUuid = TypeChecker.untrimUUID(uuid);
        String viewName = "vw_Transactions_" + trimmedUuid;
        String databaseName = plugin.getConfig().getString("database.database");
        String checkSQL = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.VIEWS WHERE TABLE_NAME = ? AND TABLE_SCHEMA = ?";

        try (PreparedStatement preparedStatement = connection.prepareStatement(checkSQL)) {
            preparedStatement.setString(1, viewName);
            preparedStatement.setString(2, databaseName);

            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next() && resultSet.getInt(1) > 0) {
                    return;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error checking for existing transaction view: " + e.getMessage());
            return;
        }

        try (Statement statement = connection.createStatement()) {
            String sql = "CREATE VIEW " + viewName + " AS "
                    + "SELECT "
                    + "    t.TransactionDatetime, "
                    + "    t.Amount, "
                    + "    sourcePlayer.PlayerName AS SourcePlayerName, "
                    + "    destinationPlayer.PlayerName AS DestinationPlayerName, "
                    + "    t.TransactionMessage AS Message, "
                    + "    CASE "
                    + "        WHEN t.Passed = 1 THEN 'Passed' "
                    + "        ELSE 'Failed' "
                    + "    END AS Passed, "
                    + "    t.PassedReason "
                    + "FROM Transactions t "
                    + "LEFT JOIN PlayerAccounts sourcePlayer ON t.Source = sourcePlayer.UUID "
                    + "LEFT JOIN PlayerAccounts destinationPlayer ON t.Destination = destinationPlayer.UUID "
                    + "WHERE t.Source = '" + trimmedUuid + "' OR t.Destination = '" + trimmedUuid + "' "
                    + "ORDER BY t.TransactionDatetime DESC;";

            statement.executeUpdate(sql);
            plugin.getLogger().info("Transaction view created for UUID: " + uuid);
        } catch (SQLException e) {
            plugin.getLogger().severe("Error creating transaction view: " + e.getMessage());
        }
    }

    public static double displayBalance(@NotNull String uuid) {
        String trimmedUuid = TypeChecker.trimUUID(uuid);
        String untrimmedUuid = TypeChecker.untrimUUID(uuid);
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
            plugin.getLogger().severe("Error viewing balance for UUID " + untrimmedUuid + ": " + e.getMessage());
        }
        return balance;
    }

    public static List<Map<String, Object>> displayTransactionsView(@NotNull String uuid, Boolean displayPassed) {
        String trimmedUuid = TypeChecker.trimUUID(uuid);
        String viewName = "vw_Transactions_" + trimmedUuid;

        String sql;
        if (displayPassed == null) {
            // Display all transactions
            sql = "SELECT TransactionDatetime, Amount, Source, Destination, TransactionMessage FROM " + viewName + " ORDER BY TransactionDateTime DESC";
        } else if (displayPassed) {
            // Display only passed transactions
            sql = "SELECT TransactionDatetime, Amount, Source, Destination, TransactionMessage FROM " + viewName + " WHERE Passed = 1 ORDER BY TransactionDateTime DESC";
        } else {
            // Display only failed transactions
            sql = "SELECT TransactionDatetime, Amount, Source, Destination, TransactionMessage FROM " + viewName + " WHERE Passed = 0 ORDER BY TransactionDateTime DESC";
        }

        List<Map<String, Object>> transactions = new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            // Iterate over the result set
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

    public static void addAutopay(String autopayName, @NotNull String uuid, @NotNull String destination,
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
        Timestamp currentTime = new Timestamp(System.currentTimeMillis());
        String sql = "DECLARE @AutopayName varchar(16) = ?;"
                + "DECLARE @UUID char(32) = ?;"
                + "AutopayDatetime DATETIME = ?;"
                + "DECLARE @Destination char(32) = ?;"
                + "DECLARE @Amount float = ?;"
                + "DECLARE @InverseFrequency int NOT NULL = ?;"
                + "DECLARE @EndsAfter int NOT NULL = ?;"
                + "BEGIN TRY"
                + "    INSERT INTO Autopays ("
                + "        Active, AutopayDatetime, AutopayName, Source, Destination,"
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
            pstmt.setTimestamp(3, currentTime);
            pstmt.setString(4, trimmedDestination);
            pstmt.setDouble(5, amount);
            pstmt.setInt(6, inverseFrequency);
            pstmt.setInt(7, endsAfter);

            pstmt.executeUpdate();
            plugin.getLogger().info("Autopay added successfully");
        } catch (SQLException e) {
            plugin.getLogger().severe("Error adding autopay: " + e.getMessage());
        }
    }

    public static void stateChangeAutopay(boolean activeState, int autopayID, @NotNull String uuid) {
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

    public static void deleteAutopay(int autopayID, @NotNull String uuid) {
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

    public static List<Map<String, Object>> viewAutopays(@NotNull String uuid) {
        String trimmedUuid = TypeChecker.trimUUID(uuid);
        String untrimmedUuid = TypeChecker.untrimUUID(uuid);
        String sql = "SELECT a.AutopayID, a.AutopayName, a.Amount, pa.PlayerName AS DestinationName, a.InverseFrequency, a.TimesLeft " +
                "FROM Autopays a " +
                "JOIN PlayerAccounts pa ON a.Destination = pa.UUID " +
                "WHERE a.Source = ? " +
                "ORDER BY a.AutopayDatetime DESC";

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
            plugin.getLogger().severe("Error viewing autopays for UUID " + untrimmedUuid + ": " + e.getMessage());
        }
        return autopays;
    }


    public static List<Map<String, Object>> listAllAccounts() {
        String sql = "SELECT PlayerName, Balance, Change, AccountDatetime AS Created FROM PlayerAccounts ORDER BY PlayerName ASC";
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

    public static void rollback(@NotNull Timestamp rollbackTime, boolean keepTransactions) {
        String getBalances = "SELECT pa.UUID, pa.Balance, COALESCE(t.NewSourceBalance, t.NewDestinationBalance) AS RollbackBalance FROM PlayerAccounts pa LEFT JOIN (SELECT DISTINCT ON (Source) Source, NewSourceBalance FROM Transactions WHERE TransactionID <= ? ORDER BY Source, TransactionID DESC) t ON pa.UUID = t.Source";
        String deleteTransactions = "DELETE FROM Transactions WHERE TransactionDatetime > ?";
        String getAutopays = "SELECT AutopayID, AutopayDatetime, Source FROM Autopays";
        String deactivateAutopays = "UPDATE Autopays SET Active = 0 WHERE CreationDate <= ?";
        String updateChangeToZero = "UPDATE PlayerAccounts SET Change = 0";

        try (Connection conn = getConnection();
             PreparedStatement pstmtGet = conn.prepareStatement(getBalances);
             PreparedStatement pstmtDelete = conn.prepareStatement(deleteTransactions);
             PreparedStatement pstmtGetAutopays = conn.prepareStatement(getAutopays);
             PreparedStatement pstmtDeactivateAutopays = conn.prepareStatement(deactivateAutopays);
             PreparedStatement pstmtUpdateChange = conn.prepareStatement(updateChangeToZero)) {

            conn.setAutoCommit(false);

            // Update Change to 0 for all player accounts
            pstmtUpdateChange.executeUpdate();

            // Get balances up to rollbackTime
            pstmtGet.setTimestamp(1, rollbackTime);
            ResultSet rs = pstmtGet.executeQuery();

            while (rs.next()) {
                String uuid = rs.getString("UUID");
                String trimmedUuid = TypeChecker.trimUUID(uuid);
                double currentBalance = rs.getDouble("Balance");
                double rollbackBalance = rs.getDouble("RollbackBalance");
                double difference = rollbackBalance - currentBalance;

                if (!keepTransactions) {
                    pstmtDelete.setTimestamp(1, rollbackTime);
                    pstmtDelete.executeUpdate();
                    // Set the player's balance to the rollback balance (without a transaction)
                    setPlayerBalance(trimmedUuid, rollbackBalance, 0);
                } else {
                    // Execute rollback adjustment (with transaction)
                    if (difference > 0) {
                        executeTransaction("system", "admin_command", null, trimmedUuid, difference, "Rollback adjustment");
                    } else if (difference < 0) {
                        double negativeDifference = 0 - difference;
                        executeTransaction("system", "admin_command", trimmedUuid, null, negativeDifference, "Rollback adjustment");
                    }
                }
            }

            ResultSet rsAutopays = pstmtGetAutopays.executeQuery();
            while (rsAutopays.next()) {
                int autopayID = rsAutopays.getInt("AutopayID");
                Timestamp creationDatetime = rsAutopays.getTimestamp("AutopayDatetime");
                String source = rsAutopays.getString("Source");
                if (creationDatetime.after(rollbackTime)) {
                    deleteAutopay(autopayID, source);
                }
            }

            pstmtDeactivateAutopays.setTimestamp(1, rollbackTime);
            pstmtDeactivateAutopays.executeUpdate();

            conn.commit();
            plugin.getLogger().info("Rollback to " + rollbackTime + " completed successfully.");
        } catch (SQLException e) {
            plugin.getLogger().severe("Error during rollback: " + e.getMessage());
        }
    }

    public static void setPlayerBalance(@NotNull String uuid, double balance, double change) {
        String trimmedUuid = TypeChecker.trimUUID(uuid);
        String untrimmedUuid = TypeChecker.untrimUUID(uuid);
        String sql = "UPDATE PlayerAccounts SET Balance = ?, Change = ? WHERE UUID = ?;";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setDouble(1, balance);
            pstmt.setDouble(1, change);
            pstmt.setString(3, trimmedUuid);
            int rowsAffected = pstmt.executeUpdate();

            if (rowsAffected > 0) {
                plugin.getLogger().info("Balance updated successfully for UUID: " + untrimmedUuid);
            } else {
                plugin.getLogger().info("No account found for UUID: " + untrimmedUuid);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error updating balance for UUID " + untrimmedUuid + ": " + e.getMessage());
        }
    }

    public static boolean migrateToDatabase() {
        try {
            FileConfiguration balanceConfig = BalanceFile.get();
            for (String key : balanceConfig.getKeys(false)) {
                double balance = balanceConfig.getDouble(key + ".balance");
                double change = balanceConfig.getDouble(key + ".change");
                String trimmedUuid = TypeChecker.trimUUID(key);

                if (!accountExists(trimmedUuid)) {
                    addAccount(trimmedUuid, balanceConfig.getString(key + ".playerName"), balance, change);
                } else {
                    setPlayerBalance(trimmedUuid, balance, change);
                }
            }
            plugin.getLogger().info("Migration to database completed successfully.");
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Error during migration to database: " + e.getMessage());
            return false;
        }
    }

    public static void migrateToBalanceFile() {
        String sqlCount = "SELECT COUNT(*) AS playerCount FROM PlayerAccounts";
        String sql = "SELECT UUID, PlayerName, Balance FROM PlayerAccounts";

        try (Connection conn = DatabaseManager.getConnection();
            PreparedStatement pstmtCount = conn.prepareStatement(sqlCount);
            ResultSet rsCount = pstmtCount.executeQuery();
            PreparedStatement pstmt = conn.prepareStatement(sql);
            ResultSet rs = pstmt.executeQuery()) {

            int playerCount = 0;
            if (rsCount.next()) {
                playerCount = rsCount.getInt("playerCount");
            }

            if (BalanceFile.get() == null) {
                BalanceFile.setup();
                BalanceFile.get().options().copyDefaults(true);
                BalanceFile.save();
            }

            FileConfiguration balanceConfig = BalanceFile.get();
            for (String key : balanceConfig.getKeys(false)) {
                balanceConfig.set(key, null);
            }

            while (rs.next()) {
                String uuid = rs.getString("UUID");
                String untrimmedUuid = TypeChecker.untrimUUID(uuid);
                String playerName = rs.getString("PlayerName");
                double balance = rs.getDouble("Balance");
                double change = rs.getDouble("Change");

                // Validate data before writing
                if (uuid != null && playerName != null && balance >= 0) {
                    balanceConfig.set(uuid + ".playerName", playerName);
                    balanceConfig.set(uuid + ".balance", balance);
                    balanceConfig.set(uuid + ".change", change);
                } else {
                    plugin.getLogger().warning("Invalid data for UUID: " + untrimmedUuid);
                }
            }

            // Save the updated configuration to balance.yml
            BalanceFile.save();
            plugin.getLogger().info("Successfully migrated " + playerCount + " players to balance.yml");

        } catch (SQLException e) {
            plugin.getLogger().severe("Error migrating to balance.yml: " + e.getMessage());
        }
    }

    public static boolean accountExists(@NotNull String uuid) {
        String sql = "SELECT COUNT(*) FROM PlayerAccounts WHERE UUID = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, TypeChecker.trimUUID(uuid));
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0; // Return true if count is greater than 0
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error checking account existence: " + e.getMessage());
        }
        return false;
    }

    public static void updatePlayerName(String uuid, String playerName) {
        String trimmedUuid = TypeChecker.trimUUID(uuid);
        String sql = "UPDATE PlayerAccounts SET PlayerName = ? WHERE UUID = ?";
    
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, playerName);
            pstmt.setString(2, trimmedUuid);
            int rowsUpdated = pstmt.executeUpdate();
    
            if (rowsUpdated > 0) {
                plugin.getLogger().info("Player name updated successfully for UUID: " + trimmedUuid);
            } else {
                plugin.getLogger().info("No account found for UUID: " + trimmedUuid);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error updating player name for UUID " + trimmedUuid + ": " + e.getMessage());
        }
    }

    public static void exportDatabase() {
        String sqlAccounts = "SELECT * FROM PlayerAccounts";
        String sqlTransactions = "SELECT * FROM Transactions";
        String sqlAutopays = "SELECT * FROM Autopays";
        String csvFilePath = "QuickEconomyExport.csv"; // Path to save the CSV file

        try (Connection conn = getConnection();
             FileWriter csvWriter = new FileWriter(csvFilePath)) {

            // Export PlayerAccounts table
            try (PreparedStatement pstmt = conn.prepareStatement(sqlAccounts);
                 ResultSet rs = pstmt.executeQuery()) {

                csvWriter.append("PlayerAccounts Table\n");
                writeResultSetToCSV(rs, csvWriter);
                csvWriter.append("\n"); // Separate tables with a blank line
            }

            // Export Transactions table
            try (PreparedStatement pstmt = conn.prepareStatement(sqlTransactions);
                 ResultSet rs = pstmt.executeQuery()) {

                csvWriter.append("Transactions Table\n");
                writeResultSetToCSV(rs, csvWriter);
                csvWriter.append("\n"); // Separate tables with a blank line
            }

            // Export Autopays table
            try (PreparedStatement pstmt = conn.prepareStatement(sqlAutopays);
                 ResultSet rs = pstmt.executeQuery()) {

                csvWriter.append("Autopays Table\n");
                writeResultSetToCSV(rs, csvWriter);
                csvWriter.append("\n"); // Separate tables with a blank line
            }

            plugin.getLogger().info("Database exported to " + csvFilePath + " successfully.");
        } catch (SQLException e) {
            plugin.getLogger().severe("Error exporting database: " + e.getMessage());
        } catch (IOException e) {
            plugin.getLogger().severe("Error writing CSV file: " + e.getMessage());
        }
    }

    private static void writeResultSetToCSV(ResultSet rs, FileWriter csvWriter) throws SQLException, IOException {
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();

        // Write the header row
        for (int i = 1; i <= columnCount; i++) {
            csvWriter.append(metaData.getColumnName(i));
            if (i < columnCount) csvWriter.append(","); // Separate columns with a comma
        }
        csvWriter.append("\n");

        // Write the data rows
        while (rs.next()) {
            for (int i = 1; i <= columnCount; i++) {
                csvWriter.append(rs.getString(i));
                if (i < columnCount) csvWriter.append(","); // Separate columns with a comma
            }
            csvWriter.append("\n");
        }
    }


}
