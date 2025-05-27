package net.derfla.quickeconomy.util;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.derfla.quickeconomy.Main;
import net.derfla.quickeconomy.file.BalanceFile;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.sql.*;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CompletionException;

import java.io.FileWriter;
import java.io.IOException;
import java.util.function.Consumer;

public class DatabaseManager {

    static Plugin plugin = Main.getInstance();
    public static HikariDataSource dataSource;
    private static final ExecutorService executorService = Main.getExecutorService();
    
    @FunctionalInterface
    interface SQLConsumer<T> {
        void accept(T t) throws SQLException;
    }

    @FunctionalInterface
    interface SQLFunction<T, R> {
        R apply(T t) throws SQLException;
    }

    // ======================================
    // ### UTILITY/INFRASTRUCTURE METHODS ###
    // ======================================

    private static <T> CompletableFuture<T> executeQueryAsync(SQLFunction<Connection, T> queryFunction) {
        return getConnectionAsync().thenCompose(conn -> {
            if (conn == null) {
                return CompletableFuture.failedFuture(new SQLException("Failed to obtain database connection."));
            }
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return queryFunction.apply(conn);
                } catch (SQLException e) {
                    plugin.getLogger().severe("SQL operation failed: " + e.getMessage());
                    throw new CompletionException(e);
                } finally {
                    try {
                        if (conn != null && !conn.isClosed()) {
                            conn.close();
                        }
                    } catch (SQLException e) {
                        plugin.getLogger().warning("Failed to close connection after query: " + e.getMessage());
                    }
                }
            }, executorService);
        });
    }

    private static CompletableFuture<Void> executeUpdateAsync(SQLConsumer<Connection> updateAction) {
        return getConnectionAsync().thenCompose(conn -> {
            if (conn == null) {
                return CompletableFuture.failedFuture(new SQLException("Failed to obtain database connection."));
            }
            return CompletableFuture.runAsync(() -> {
                try {
                    updateAction.accept(conn);
                } catch (SQLException e) {
                    plugin.getLogger().severe("SQL update operation failed: " + e.getMessage());
                    throw new CompletionException(e);
                } finally {
                    try {
                        if (conn != null && !conn.isClosed()) {
                            conn.close();
                        }
                    } catch (SQLException e) {
                        plugin.getLogger().warning("Failed to close connection after update: " + e.getMessage());
                    }
                }
            }, executorService);
        });
    }

    public static CompletableFuture<Connection> getConnectionAsync() {
        return CompletableFuture.supplyAsync(() -> {
            if (dataSource == null) {
                throw new IllegalStateException("DataSource is not initialized. Call connectToDatabase() first.");
            }
            try {
                return dataSource.getConnection();
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to get connection: " + e.getMessage());
                return null;
            }
        }, executorService);
    }

    public static void connectToDatabase() {
        HikariConfig config = new HikariConfig();

        String type = plugin.getConfig().getString("database.type");
        if ("mysql".equalsIgnoreCase(type)) {
            String host = plugin.getConfig().getString("database.host");
            int port = plugin.getConfig().getInt("database.port");if (port < 1 || port > 65535) {
                port = 3306;
                plugin.getLogger().warning("Database port must be between 1 and 65535, using default (3306).");
            }
            String database = plugin.getConfig().getString("database.database");
            String user = plugin.getConfig().getString("database.username");
            String password = plugin.getConfig().getString("database.password");

            String url = "jdbc:mysql://" + host + ":" + port + "/" + database;

            config.setJdbcUrl(url);
            config.setUsername(user);
            config.setPassword(password);

            int poolSize = plugin.getConfig().getInt("poolSize");
            if (poolSize < 1 || poolSize > 50) {
                poolSize = 10;
                plugin.getLogger().warning("Database pool size must be between 1 and 50, using default (10).");
            }
            // Optional HikariCP settings
            config.setMaximumPoolSize(poolSize); // Max number of connections in the pool
            config.setConnectionTimeout(30000); // 30 seconds timeout for getting a connection
            config.setIdleTimeout(600000); // 10 minutes before an idle connection is closed
            config.setMaxLifetime(1800000); // 30 minutes max lifetime for a connection
        } else if ("sqlite".equalsIgnoreCase(type)) {
            String filePath = plugin.getConfig().getString("database.file");
            String url = "jdbc:sqlite:" + filePath;
            config.setJdbcUrl(url);
            config.setPoolName("QuickEconomy-SQLite-Pool");
            config.setMaximumPoolSize(plugin.getConfig().getInt("database.sqlite_pool_size", 1));
            config.setConnectionTestQuery("SELECT 1");
            config.setLeakDetectionThreshold(10000); // 10 seconds
        }

        dataSource = new HikariDataSource(config);
        plugin.getLogger().info("Database connection pool established.");
    }

    public static void closePool() {
        if (dataSource != null) {
            dataSource.close();
            plugin.getLogger().info("Database connection pool closed.");
        }
    }

    public static void shutdownExecutorService() {
        executorService.shutdown();
    }

    // ===============================
    // ### TABLE/SCHEMA MANAGEMENT ###
    // ===============================

    public static CompletableFuture<Void> createTables() {
        return getConnectionAsync().thenCompose(conn -> {
            if (conn == null) {
                plugin.getLogger().severe("Failed to get connection for createTables. Tables will not be created.");
                return CompletableFuture.failedFuture(new SQLException("Failed to obtain database connection for createTables."));
            }
            List<String> tableCreationQueries = new ArrayList<>();

            String PlayerAccounts = "CREATE TABLE IF NOT EXISTS PlayerAccounts ("
                    + "  UUID char(32) NOT NULL,"
                    + "  AccountDatetime varchar(23) NOT NULL,"
                    + "  PlayerName varchar(16) NOT NULL,"
                    + "  Balance float NOT NULL DEFAULT 0,"
                    + "  BalChange float NOT NULL DEFAULT 0,"
                    + "  PRIMARY KEY (UUID)"
                    + ");";
            tableCreationQueries.add(PlayerAccounts);

            String Transactions = "CREATE TABLE IF NOT EXISTS Transactions ("
                    + "  TransactionID bigint NOT NULL AUTO_INCREMENT,"
                    + "  TransactionDatetime varchar(23) NOT NULL,"
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
                    + "  PRIMARY KEY (TransactionID),";
            // MySQL specific foreign key syntax, for SQLite this might need adjustment
            if ("mysql".equalsIgnoreCase(plugin.getConfig().getString("database.type"))) {
                Transactions += "  FOREIGN KEY (Source) REFERENCES PlayerAccounts(UUID),"
                             + "  FOREIGN KEY (Destination) REFERENCES PlayerAccounts(UUID)";
            }
            Transactions += ");";
            tableCreationQueries.add(Transactions);

            String Autopays = "CREATE TABLE IF NOT EXISTS Autopays ("
                    + "  AutopayID bigint NOT NULL AUTO_INCREMENT,"
                    + "  AutopayDatetime DATETIME NOT NULL,"
                    + "  Active tinyint(1) NOT NULL DEFAULT 1,"
                    + "  AutopayName varchar(16),"
                    + "  Source char(32),"
                    + "  Destination char(32),"
                    + "  Amount float NOT NULL,"
                    + "  InverseFrequency int NOT NULL,"
                    + "  TimesLeft int,"
                    + "  PRIMARY KEY (AutopayID),";
            if ("mysql".equalsIgnoreCase(plugin.getConfig().getString("database.type"))) {
                Autopays += "  FOREIGN KEY (Source) REFERENCES PlayerAccounts(UUID),"
                         + "  FOREIGN KEY (Destination) REFERENCES PlayerAccounts(UUID)";
            }
            Autopays += ");";
            tableCreationQueries.add(Autopays);

            String EmptyShops = "CREATE TABLE IF NOT EXISTS EmptyShops ("
                    + "  Coordinates varchar(32) NOT NULL,"
                    + "  Owner1 char(32),"
                    + "  Owner2 char(32),"
                    + "  PRIMARY KEY (Coordinates)"
                    + ");";
            tableCreationQueries.add(EmptyShops);

            CompletableFuture<Void> allTablesFuture = CompletableFuture.completedFuture(null);

            for (String query : tableCreationQueries) {
                final String currentQuery = query; // Need to be effectively final for lambda
                allTablesFuture = allTablesFuture.thenCompose(v ->
                        CompletableFuture.runAsync(() -> {
                            try (Statement statement = conn.createStatement()) {
                                statement.executeUpdate(currentQuery);
                                String tableName = currentQuery.substring(currentQuery.indexOf("EXISTS ") + 7, currentQuery.indexOf(" ("));
                                plugin.getLogger().info("Table " + tableName + " created or already exists.");
                            } catch (SQLException e) {
                                String tableNameAttempt = "Unknown";
                                try {
                                    tableNameAttempt = currentQuery.substring(currentQuery.indexOf("EXISTS ") + 7, currentQuery.indexOf(" ("));
                                } catch (Exception ignored) {}
                                plugin.getLogger().severe("Error for table " + tableNameAttempt + ": " + e.getMessage());
                                throw new CompletionException(e); // Propagate error
                            }
                        }, executorService)
                );
            }

            return allTablesFuture.whenComplete((res, ex) -> { // Ensures conn is closed after all table ops
                try {
                    if (conn != null && !conn.isClosed()) {
                        conn.close();
                    }
                } catch (SQLException e) {
                    plugin.getLogger().warning("Failed to close connection after createTables operations: " + e.getMessage());
                }
            });
        }).whenComplete((result, ex) -> {
            if (ex != null) {
                plugin.getLogger().severe("Error during createTables database operations: " + ex.getMessage());
            } else {
                plugin.getLogger().info("Database table creation/verification process completed.");
            }
        });
    }

    // Helper method for creating views
    private static CompletableFuture<Void> createViewInternal(String viewName, String databaseName, String createViewSql, String[] createViewParams, String logMsgContext) {
        String checkSQL = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.VIEWS WHERE TABLE_NAME = ? AND TABLE_SCHEMA = ?";

        return executeQueryAsync(conn -> { // Manages conn for the check
            try (PreparedStatement preparedStatement = conn.prepareStatement(checkSQL)) {
                preparedStatement.setString(1, viewName);
                preparedStatement.setString(2, databaseName);
                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    return resultSet.next() && resultSet.getInt(1) > 0; // Returns true if view exists
                }
            }
        }).thenCompose(viewExists -> {
            if (viewExists) {
                // plugin.getLogger().info("View " + viewName + " already exists for " + logMsgContext); // Optional: reduce log verbosity
                return CompletableFuture.completedFuture(null);
            } else {
                return executeUpdateAsync(conn2 -> { // Manages conn2 for the creation
                    try (PreparedStatement createViewStmt = conn2.prepareStatement(createViewSql)) {
                        for (int i = 0; i < createViewParams.length; i++) {
                            createViewStmt.setString(i + 1, createViewParams[i]);
                        }
                        createViewStmt.executeUpdate();
                        plugin.getLogger().info("View " + viewName + " created for " + logMsgContext);
                    }
                });
            }
        }).exceptionally(ex -> {
            plugin.getLogger().severe("Error during view management for " + viewName + " (" + logMsgContext + "): " + ex.getMessage());
            // It's often better to let the exception propagate to the caller for specific handling
            // or return a failed future if this method is part of a larger chain that expects it.
            // For now, returning null (which will complete the future normally with null) to match previous void logic.
            throw new CompletionException(ex); // Propagate as a CompletionException
        });
    }

    private static CompletableFuture<Void> createTransactionsView(@NotNull String uuid) {
        String trimmedUuid = TypeChecker.trimUUID(uuid);
        String untrimmedUuid = TypeChecker.untrimUUID(uuid);
        String viewName = "vw_Transactions_" + trimmedUuid;
        String databaseName = plugin.getConfig().getString("database.database"); // Assumes this config is available and correct

        String sql = "CREATE VIEW " + viewName + " AS "
                + "SELECT "
                + "    t.TransactionDatetime, "
                + "    t.Amount, "
                + "    t.Source AS SourceUUID, "
                + "    t.Destination AS DestinationUUID, "
                + "    pa.PlayerName AS SourcePlayerName, "
                + "    pa2.PlayerName AS DestinationPlayerName, "
                + "    t.TransactionMessage AS Message, "
                + "    CASE "
                + "        WHEN t.Passed = 1 THEN 'Passed' "
                + "        ELSE 'Failed' "
                + "    END AS Passed, "
                + "    t.PassedReason "
                + "FROM Transactions t "
                + "LEFT JOIN PlayerAccounts pa ON t.Source = pa.UUID "
                + "LEFT JOIN PlayerAccounts pa2 ON t.Destination = pa2.UUID "
                + "WHERE t.Source = ? OR t.Destination = ? "
                + "ORDER BY t.TransactionDatetime ASC;";
        String[] params = {trimmedUuid, trimmedUuid};
        return createViewInternal(viewName, databaseName, sql, params, "UUID: " + untrimmedUuid);
    }

    private static CompletableFuture<Void> createEmptyShopsView(@NotNull String uuid) {
        String trimmedUuid = TypeChecker.trimUUID(uuid);
        String untrimmedUuid = TypeChecker.untrimUUID(uuid);
        String viewName = "vw_EmptyShops_" + trimmedUuid;
        String databaseName = plugin.getConfig().getString("database.database");

        String sql = "CREATE VIEW " + viewName + " AS "
                + "SELECT "
                + "    e.Coordinates "
                + "FROM EmptyShops e "
                + "WHERE Owner1 = ? OR Owner2 = ?;";
        String[] params = {trimmedUuid, trimmedUuid};
        return createViewInternal(viewName, databaseName, sql, params, "UUID: " + untrimmedUuid);
    }

    // ==========================
    // ### ACCOUNT MANAGEMENT ###
    // ==========================

    public static CompletableFuture<Void> addAccount(@NotNull String uuid, @NotNull String playerName, double balance, double change, Consumer<Void> callback) {
        String trimmedUuid = TypeChecker.trimUUID(uuid);
        Instant currentTime = Instant.now();
        String currentTimeString = TypeChecker.convertToUTC(currentTime.atZone(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        return accountExists(trimmedUuid).thenCompose(exists -> {
            if (exists) {
                plugin.getLogger().info("Account already exists for player with UUID: " + trimmedUuid);
                return CompletableFuture.completedFuture(null);
            } else {
                String insertSql = "INSERT INTO PlayerAccounts (UUID, AccountDatetime, PlayerName, Balance, BalChange) "
                        + "VALUES (?, ?, ?, ?, ?)";
                return executeUpdateAsync(conn -> {
                    try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
                        pstmt.setString(1, trimmedUuid);
                        pstmt.setString(2, currentTimeString); // Use the converted UTC time
                        pstmt.setString(3, playerName);
                        pstmt.setDouble(4, balance);
                        pstmt.setDouble(5, change);
                        int rowsInserted = pstmt.executeUpdate();

                        if (rowsInserted > 0) {
                            plugin.getLogger().info("New player account added successfully for " + playerName);
                        }
                    }
                });
            }
        }).thenCompose(v -> createTransactionsView(trimmedUuid))
        .exceptionally(ex -> {
            plugin.getLogger().severe("Error during addAccount operation for UUID: " + trimmedUuid + " PlayerName: " + playerName + " - " + ex.getMessage());
            if (ex instanceof CompletionException) throw (CompletionException) ex;
            throw new CompletionException(ex);
        });
    }

    public static CompletableFuture<Boolean> accountExists(@NotNull String uuid) {
        String sql = "SELECT COUNT(*) FROM PlayerAccounts WHERE UUID = ?";
        String trimmedUuid = TypeChecker.trimUUID(uuid);

        return executeQueryAsync(conn -> {
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, trimmedUuid);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1) > 0; // Return true if count is greater than 0
                    }
                }
            }
            return false;
        });
    }

    public static CompletableFuture<Void> updatePlayerName(String uuid, String newPlayerName) {
        String sql = "UPDATE PlayerAccounts SET PlayerName = ? WHERE UUID = ?";
        String trimmedUuid = TypeChecker.trimUUID(uuid);
        String untrimmedUuid = TypeChecker.untrimUUID(uuid);

        return executeUpdateAsync(conn -> {
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, newPlayerName);
                pstmt.setString(2, trimmedUuid);
                int rowsAffected = pstmt.executeUpdate();

                if (rowsAffected > 0) {
                    plugin.getLogger().info("Updated player name for UUID " + untrimmedUuid + ": " + newPlayerName);
                } else {
                    plugin.getLogger().info("No account found for UUID: " + untrimmedUuid);
                }
            }
        }).exceptionally(ex -> {
            plugin.getLogger().severe("Error updating player name for UUID: " + untrimmedUuid + " - " + ex.getMessage());
            if (ex instanceof CompletionException) throw (CompletionException) ex;
            throw new CompletionException(ex);
        });
    }

    public static CompletableFuture<Double> displayBalance(@NotNull String uuid) {
        String trimmedUuid = TypeChecker.trimUUID(uuid);
        String sql = "SELECT Balance FROM PlayerAccounts WHERE UUID = ?";

        return executeQueryAsync(conn -> {
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, trimmedUuid);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getDouble("Balance"); // Return the balance
                    }
                }
            }
            return 0.0; // Return 0 if no balance found or an error occurred
        });
    }

    // Synchronous method for rollback purposes
    private static double displayBalanceSync(Connection conn, @NotNull String uuid) throws SQLException {
        String trimmedUuid = TypeChecker.trimUUID(uuid);
        String sql = "SELECT Balance FROM PlayerAccounts WHERE UUID = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, trimmedUuid);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble("Balance");
                }
            }
        }
        return 0.0; // Return 0 if no balance found
    }

    public static CompletableFuture<Void> setPlayerBalance(@NotNull String uuid, double balance, double change) {
        String trimmedUuid = TypeChecker.trimUUID(uuid);
        String untrimmedUuid = TypeChecker.untrimUUID(uuid);
        String sql = "UPDATE PlayerAccounts SET Balance = ?, BalChange = ? WHERE UUID = ?;";

        return executeUpdateAsync(conn -> {
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setDouble(1, balance);
                pstmt.setDouble(2, change);
                pstmt.setString(3, trimmedUuid);
                int rowsAffected = pstmt.executeUpdate();

                if (rowsAffected > 0) {
                    plugin.getLogger().info("Balance updated successfully for UUID: " + untrimmedUuid);
                } else {
                    plugin.getLogger().info("No account found for UUID: " + untrimmedUuid);
                }
            }
        }).exceptionally(ex -> {
            plugin.getLogger().severe("Error setting player balance for UUID: " + untrimmedUuid + " - " + ex.getMessage());
            if (ex instanceof CompletionException) throw (CompletionException) ex;
            throw new CompletionException(ex);
        });
    }

    // Synchronous method for rollback purposes
    private static void setPlayerBalanceSync(Connection conn, @NotNull String uuid, double balance, double change) throws SQLException {
        String trimmedUuid = TypeChecker.trimUUID(uuid);
        String sql = "UPDATE PlayerAccounts SET Balance = ?, BalChange = ? WHERE UUID = ?;";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setDouble(1, balance);
            pstmt.setDouble(2, change);
            pstmt.setString(3, trimmedUuid);
        }
    }

    public static CompletableFuture<Double> getPlayerBalanceChange(String uuid) {
        String trimmedUUID = TypeChecker.trimUUID(uuid);
        String sql = "SELECT BalChange FROM PlayerAccounts WHERE UUID = ?";

        return executeQueryAsync(conn -> {
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, trimmedUUID);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getDouble("BalChange");
                    }
                }
            }
            return 0.0; // Return 0.0 if no change found
        });
    }

    // Synchronous method for rollback purposes
    private static double getPlayerBalanceChangeSync(Connection conn, String uuid) throws SQLException {
        String trimmedUUID = TypeChecker.trimUUID(uuid);
        String sql = "SELECT BalChange FROM PlayerAccounts WHERE UUID = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, trimmedUUID);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble("BalChange");
                }
            }
        }
        return 0.0; // Return 0.0 if no change found
    }

    public static CompletableFuture<Void> setPlayerBalanceChange(@NotNull String uuid, double change) {
        String trimmedUuid = TypeChecker.trimUUID(uuid);
        String sql = "UPDATE PlayerAccounts SET BalChange = ? WHERE UUID = ?;";

        return executeUpdateAsync(conn -> {
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setDouble(1, change);
                pstmt.setString(2, trimmedUuid);
                pstmt.executeUpdate();
            }
        }).exceptionally(ex -> {
            plugin.getLogger().severe("Error setting player balance change for UUID: " + uuid + " - " + ex.getMessage());
            if (ex instanceof CompletionException) throw (CompletionException) ex;
            throw new CompletionException(ex);
        });
    }

    public static CompletableFuture<List<String>> listAllAccounts() {
        String sql = "SELECT PlayerName, Balance, BalChange, AccountDatetime AS Created FROM PlayerAccounts ORDER BY PlayerName ASC";
        
        return executeQueryAsync(conn -> {
            List<String> accounts = new ArrayList<>();
            try (PreparedStatement pstmt = conn.prepareStatement(sql);
                 ResultSet rs = pstmt.executeQuery()) {

                while (rs.next()) {
                    String playerName = rs.getString("PlayerName");
                    double balance = rs.getDouble("Balance");
                    String accountDatetimeUTC = rs.getString("Created"); // Get the UTC datetime
                    String accountDatetimeLocal = TypeChecker.convertToLocalTime(accountDatetimeUTC); // Convert to local time

                    String accountInfo = playerName + ": " + balance + " (Created: " + accountDatetimeLocal + ")";
                    accounts.add(accountInfo);
                }
            }
            return accounts; // Return the list of accounts
        });
    }

    public static CompletableFuture<String> getUUID(String playerName) {
        String sql = "SELECT UUID FROM PlayerAccounts WHERE PlayerName = ?";
        
        return executeQueryAsync(conn -> {
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, playerName);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("UUID");
                    }
                }
            }
            return null; // Return null if not found
        });
    }

    // ==============================
    // ### TRANSACTION MANAGEMENT ###
    // ==============================

    public static CompletableFuture<Void> executeTransaction(@NotNull String transactType, @NotNull String induce, String source,
                                          String destination, double amount, String transactionMessage) {
        String trimmedSource = TypeChecker.trimUUID(source);
        String trimmedDestination = TypeChecker.trimUUID(destination);
        Instant currentTime = Instant.now(); // Use Instant for UTC time
        // For VARCHAR(23), ensure .SSS for milliseconds and it's UTC for Transactions.TransactionDatetime
        String currentUTCTimeString = currentTime.atZone(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));

        return executeUpdateAsync(conn -> {
            conn.setAutoCommit(false);  // Begin transaction
            
            try {
                // SQL to handle the transaction
                String sqlUpdateSource = "UPDATE PlayerAccounts SET Balance = Balance - ? WHERE UUID = ?";
                String sqlUpdateDestination = "UPDATE PlayerAccounts SET Balance = Balance + ? WHERE UUID = ?";
                String sqlInsertTransaction = "INSERT INTO Transactions (TransactionDatetime, TransactionType, Induce, Source, Destination, NewSourceBalance, NewDestinationBalance, Amount, Passed, TransactionMessage)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

                // Update source account balance if applicable
                Double newSourceBalance = null;
                if (trimmedSource != null) {
                    try (PreparedStatement pstmtUpdateSource = conn.prepareStatement(
                            "SELECT Balance FROM PlayerAccounts WHERE UUID = ?")) {
                        pstmtUpdateSource.setString(1, trimmedSource);
                        ResultSet rs = pstmtUpdateSource.executeQuery();
                        if (rs.next()) {
                            newSourceBalance = rs.getDouble(1) - amount;  // Calculate new source balance
                        }
                    }

                    try (PreparedStatement pstmtUpdateSource = conn.prepareStatement(sqlUpdateSource)) {
                        pstmtUpdateSource.setDouble(1, amount);
                        pstmtUpdateSource.setString(2, trimmedSource);
                        pstmtUpdateSource.executeUpdate();
                    }
                }

                // Update destination account balance if applicable
                Double newDestinationBalance = null;
                if (trimmedDestination != null) {
                    try (PreparedStatement pstmtUpdateDestination = conn.prepareStatement(
                            "SELECT Balance FROM PlayerAccounts WHERE UUID = ?")) {
                        pstmtUpdateDestination.setString(1, trimmedDestination);
                        ResultSet rs = pstmtUpdateDestination.executeQuery();
                        if (rs.next()) {
                            newDestinationBalance = rs.getDouble(1) + amount;  // Calculate new destination balance
                        }
                    }

                    try (PreparedStatement pstmtUpdateDestination = conn.prepareStatement(sqlUpdateDestination)) {
                        pstmtUpdateDestination.setDouble(1, amount);
                        pstmtUpdateDestination.setString(2, trimmedDestination);
                        pstmtUpdateDestination.executeUpdate();
                    }
                }

                // Insert into Transactions table
                try (PreparedStatement pstmtInsertTransaction = conn.prepareStatement(sqlInsertTransaction)) {
                    pstmtInsertTransaction.setString(1, currentUTCTimeString); // Use the formatted UTC SSS time
                    pstmtInsertTransaction.setString(2, transactType);                   // TransactionType
                    pstmtInsertTransaction.setString(3, induce);                         // Induce
                    pstmtInsertTransaction.setString(4, trimmedSource);                  // Source
                    pstmtInsertTransaction.setString(5, trimmedDestination);             // Destination
                    pstmtInsertTransaction.setObject(6, newSourceBalance);               // NewSourceBalance (nullable)
                    pstmtInsertTransaction.setObject(7, newDestinationBalance);          // NewDestinationBalance (nullable)
                    pstmtInsertTransaction.setDouble(8, amount);                         // Amount
                    pstmtInsertTransaction.setInt(9, 1);                               // Passed (always 1 if successful)
                    pstmtInsertTransaction.setString(10, transactionMessage);            // TransactionMessage
                    pstmtInsertTransaction.executeUpdate();
                }

                // Commit the transaction
                conn.commit();
                Balances.addPlayerBalanceChange(trimmedDestination, (float) amount);
            } catch (SQLException e) {
                conn.rollback(); // Rollback on error
                if(trimmedDestination != null && trimmedSource != null) {
                    plugin.getLogger().severe("Error executing transaction from " + trimmedSource + " to " + trimmedDestination + ": " + e.getMessage());
                } else if (trimmedSource != null) {
                    plugin.getLogger().severe("Error executing transaction from " + trimmedSource + ": " + e.getMessage());
                } else if (trimmedDestination != null) {
                    plugin.getLogger().severe("Error executing transaction to " + trimmedDestination + ": " + e.getMessage());
                } else {
                    plugin.getLogger().severe("Error executing transaction: " + e.getMessage());
                }
                throw e; // Re-throw to let executeUpdateAsync handle it
            }
        }).exceptionally(ex -> {
            plugin.getLogger().severe("Outer error during executeTransaction: " + ex.getMessage());
            if (ex instanceof CompletionException) throw (CompletionException) ex;
            throw new CompletionException("Transaction execution failed unexpectedly", ex);
        });
    }

    public static CompletableFuture<String> displayTransactionsView(@NotNull String uuid, Boolean displayPassed, int page) {
        String trimmedUuid = TypeChecker.trimUUID(uuid);
        String viewName = "vw_Transactions_" + trimmedUuid;
        int pageSize = 10;
        // Calculate the offset for pagination
        int offset = (page - 1) * pageSize;
        String sql;

        if (displayPassed == null) {
            // Display all transactions
            sql = "SELECT TransactionDatetime, Amount, SourceUUID, DestinationUUID, SourcePlayerName, DestinationPlayerName, Message FROM " + viewName + " ORDER BY TransactionDatetime ASC LIMIT ? OFFSET ?";
        } else if (displayPassed) {
            // Display only passed transactions
            sql = "SELECT TransactionDatetime, Amount, SourceUUID, DestinationUUID, SourcePlayerName, DestinationPlayerName, Message FROM " + viewName + " WHERE Passed = 'Passed' ORDER BY TransactionDatetime ASC LIMIT ? OFFSET ?";
        } else {
            // Display only failed transactions
            sql = "SELECT TransactionDatetime, Amount, SourceUUID, DestinationUUID, SourcePlayerName, DestinationPlayerName, Message FROM " + viewName + " WHERE Passed = 'Failed' ORDER BY TransactionDatetime ASC LIMIT ? OFFSET ?";
        }

        return executeQueryAsync(conn -> {
            StringBuilder transactions = new StringBuilder();
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, pageSize); // Set the page size
                pstmt.setInt(2, offset); // Set the offset for pagination
                ResultSet rs = pstmt.executeQuery();

                // Iterate over the result set
                while (rs.next()) {
                    String dateTimeUTC = rs.getString("TransactionDatetime");
                    String dateTimeLocal = TypeChecker.convertToLocalTime(dateTimeUTC); // Convert to local time
                    Double amount = rs.getDouble("Amount");
                    String sourceUUID = rs.getString("SourceUUID");
                    String destinationUUID = rs.getString("DestinationUUID");
                    String sourcePlayerName = rs.getString("SourcePlayerName");
                    String destinationPlayerName = rs.getString("DestinationPlayerName");
                    String message = rs.getString("Message");
                    transactions.append(dateTimeLocal).append(" ").append(amount);
                    if (sourcePlayerName == null) {
                        // Deposit to bank
                        transactions.append(" -> ").append("[BANK]");
                    } else if (destinationPlayerName == null) {
                        // Withdraw from bank
                        transactions.append(" <- ").append("[BANK]");
                    } else if (sourceUUID.equalsIgnoreCase(trimmedUuid)) {
                        transactions.append(" -> ").append(destinationPlayerName);
                    } else if (destinationUUID.equalsIgnoreCase(trimmedUuid)) {
                        transactions.append(" <- ").append(sourcePlayerName);
                    }
                    if (message != null) {
                        transactions.append(" ").append(message);
                    }
                    transactions.append("\n");
                }
            }
            return transactions.toString();
        });
    }

    // ==========================
    // ### AUTOPAY MANAGEMENT ###
    // ==========================

    public static CompletableFuture<Void> addAutopay(String autopayName, @NotNull String uuid, @NotNull String destination,
                                  double amount, int inverseFrequency, int timesLeft) {
        String trimmedUuid = TypeChecker.trimUUID(uuid);
        String trimmedDestination = TypeChecker.trimUUID(destination);

        if (amount < 0) {
            plugin.getLogger().severe("Error: Amount must be greater than 0.");
            return CompletableFuture.failedFuture(new IllegalArgumentException("Amount must be greater than 0."));
        }
        if (inverseFrequency < 0) {
            plugin.getLogger().severe("Error: InverseFrequency must be greater than 0.");
            return CompletableFuture.failedFuture(new IllegalArgumentException("InverseFrequency must be greater than 0."));
        }
        if (timesLeft < 0) { // timesLeft can be 0 for indefinite, or > 0 for specific count
            plugin.getLogger().severe("Error: TimesLeft must be 0 (for continuous) or greater.");
            return CompletableFuture.failedFuture(new IllegalArgumentException("TimesLeft must be 0 or greater."));
        }
        
        Instant currentTime = Instant.now(); // Use Instant for UTC time
        // AutopayDatetime is DATETIME SQL type, format "yyyy-MM-dd HH:mm:ss" in UTC
        String autopayDateTimeFormatted = currentTime.atZone(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        String sql = "INSERT INTO Autopays (" +
                "    Active, AutopayDatetime, AutopayName, Source, Destination," +
                "    Amount, InverseFrequency, TimesLeft" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?);";

        return executeUpdateAsync(conn -> {
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, 1); // Active
                pstmt.setString(2, autopayDateTimeFormatted); // AutopayDatetime formatted for SQL DATETIME (UTC)
                pstmt.setString(3, autopayName); // AutopayName
                pstmt.setString(4, trimmedUuid); // Source
                pstmt.setString(5, trimmedDestination); // Destination
                pstmt.setDouble(6, amount); // Amount
                pstmt.setInt(7, inverseFrequency); // InverseFrequency
                pstmt.setInt(8, timesLeft); // TimesLeft

                pstmt.executeUpdate();
                plugin.getLogger().info("Autopay added successfully");
            }
        }).exceptionally(ex -> {
            plugin.getLogger().severe("Error adding autopay for Source: " + trimmedUuid + ", Name: " + autopayName + " - " + ex.getMessage());
            if (ex instanceof CompletionException) throw (CompletionException) ex;
            throw new CompletionException(ex);
        });
    }

    public static CompletableFuture<List<Map<String, Object>>> viewAutopays(@NotNull String uuid) {
        String trimmedUuid = TypeChecker.trimUUID(uuid);
        String sql = "SELECT a.AutopayID, a.AutopayName, a.Amount, pa.PlayerName AS DestinationName, a.InverseFrequency, a.TimesLeft " +
                "FROM Autopays a " +
                "JOIN PlayerAccounts pa ON a.Destination = pa.UUID " +
                "WHERE a.Source = ? " +
                "ORDER BY a.AutopayDatetime DESC";

        return executeQueryAsync(conn -> {
            List<Map<String, Object>> autopays = new ArrayList<>();
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
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
            }
            return autopays; // Return the list of autopays
        });
    }

    public static CompletableFuture<Void> stateChangeAutopay(boolean activeState, int autopayID, @NotNull String uuid) {
        String trimmedUuid = TypeChecker.trimUUID(uuid);
        String sql = "UPDATE Autopays SET Active = ? WHERE AutopayID = ? AND Source = ?;";

        return executeUpdateAsync(conn -> {
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
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
                throw e; // Re-throw to let executeUpdateAsync handle it
            }
        }).exceptionally(ex -> {
            plugin.getLogger().severe("Outer error toggling autopay #" + autopayID + " - " + ex.getMessage());
            if (ex instanceof CompletionException) throw (CompletionException) ex;
            throw new CompletionException(ex);
        });
    }

    public static CompletableFuture<Void> deleteAutopay(int autopayID, @NotNull String uuid) {
        String trimmedUuid = TypeChecker.trimUUID(uuid);
        String sql = "DELETE FROM Autopays WHERE AutopayID = ? AND Source = ?;";

        return executeUpdateAsync(conn -> {
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, autopayID);
                pstmt.setString(2, trimmedUuid);

                int rowsAffected = pstmt.executeUpdate();
                if (rowsAffected > 0) {
                    plugin.getLogger().info("Autopay deleted successfully");
                } else {
                    plugin.getLogger().info("Autopay not found. No deletion was performed.");
                }
            }
        }).exceptionally(ex -> {
            plugin.getLogger().severe("Error deleting autopay #" + autopayID + ", Source: " + trimmedUuid + " - " + ex.getMessage());
            if (ex instanceof CompletionException) throw (CompletionException) ex;
            throw new CompletionException(ex);
        });
    }

    // =============================
    // ### EMPTY SHOP MANAGEMENT ###
    // =============================

    public static CompletableFuture<Boolean> insertEmptyShop(@NotNull String coordinates, @NotNull String owner1, String owner2) {
        String Owner1 = TypeChecker.trimUUID(owner1);
        final String Owner2 = !owner2.isEmpty() ? TypeChecker.trimUUID(owner2) : "";

        return emptyShopExists(coordinates).thenCompose(exists -> {
            if (exists) {
                // Update the owners of the existing shop
                String updateSql = "UPDATE EmptyShops SET Owner1 = ?, Owner2 = ? WHERE Coordinates = ?";
                return executeUpdateAsync(conn -> {
                    try (PreparedStatement pstmt = conn.prepareStatement(updateSql)) {
                        pstmt.setString(1, Owner1);
                        pstmt.setString(2, Owner2);
                        pstmt.setString(3, coordinates);
                        pstmt.executeUpdate();
                        plugin.getLogger().info("Empty shop at " + coordinates + " updated with new owners.");
                    }
                }).thenCompose(v -> { // Use thenCompose for sequential async operations
                    CompletableFuture<Void> future1 = createEmptyShopsView(owner1);
                    if (Owner2 != null && !Owner2.isEmpty()) {
                        return future1.thenCompose(v2 -> createEmptyShopsView(Owner2));
                    }
                    return future1;
                }).thenApply(v -> true); // Return true if an existing shop was updated
            } else {
                // Insert a new empty shop
                String insertSql = "INSERT INTO EmptyShops (Coordinates, Owner1, Owner2) VALUES (?, ?, ?)";
                return executeUpdateAsync(conn -> {
                    try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
                        pstmt.setString(1, coordinates);
                        pstmt.setString(2, Owner1);
                        pstmt.setString(3, Owner2);
                        pstmt.executeUpdate();
                        plugin.getLogger().info("Empty shop registered at " + coordinates);
                    }
                }).thenCompose(v -> { // Use thenCompose for sequential async operations
                    CompletableFuture<Void> future1 = createEmptyShopsView(owner1);
                    if (Owner2 != null && !Owner2.isEmpty()) {
                        return future1.thenCompose(v2 -> createEmptyShopsView(Owner2));
                    }
                    return future1;
                }).thenApply(v -> false); // Return false if a new shop was created
            }
        }).exceptionally(ex -> {
            plugin.getLogger().severe("Error registering empty shop for coords: " + coordinates + " - " + ex.getMessage());
            if (ex instanceof CompletionException) throw (CompletionException) ex;
            throw new CompletionException(ex);
        });
    }

    private static CompletableFuture<Boolean> emptyShopExists(@NotNull String coordinates) {
        String sql = "SELECT COUNT(*) FROM EmptyShops WHERE Coordinates = ?";
        
        return executeQueryAsync(conn -> {
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, coordinates);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1) > 0; // Return true if the shop exists
                    }
                }
            }
            return false; // Return false if the shop does not exist or an error occurred
        });
    }

    public static CompletableFuture<List<String>> displayEmptyShopsView(@NotNull String uuid) {
        String trimmedUuid = TypeChecker.trimUUID(uuid);
        String viewName = "vw_EmptyShops_" + trimmedUuid;
        String checkViewSQL = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.VIEWS WHERE TABLE_NAME = ? AND TABLE_SCHEMA = ?";
        String databaseName = plugin.getConfig().getString("database.database");

        return executeQueryAsync(conn -> {
            List<String> emptyShops = new ArrayList<>();
            
            // Check if the view for that player exists
            try (PreparedStatement pstmt = conn.prepareStatement(checkViewSQL)) {
                pstmt.setString(1, viewName);
                pstmt.setString(2, databaseName);

                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next() && rs.getInt(1) == 0) {
                        return emptyShops; // Return empty list if the view does not exist
                    }
                }
            }

            // If the view exists, proceed to retrieve the coordinates
            String sql = "SELECT Coordinates FROM " + viewName + ";";
            try (PreparedStatement pstmt = conn.prepareStatement(sql);
                 ResultSet rs = pstmt.executeQuery()) {

                while (rs.next()) {
                    String coordinates = rs.getString("Coordinates");
                    emptyShops.add(coordinates);
                }
            }

            return emptyShops; // Return the list of empty shops
        });
    }

    public static CompletableFuture<Void> removeEmptyShop(String coordinates) {
        String sql = "DELETE FROM EmptyShops WHERE Coordinates = ?;";
        
        return executeUpdateAsync(conn -> {
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, coordinates);
                int rowsAffected = pstmt.executeUpdate();
                if (rowsAffected > 0) {
                    plugin.getLogger().info("Empty shop at " + coordinates + " removed from the database.");
                } else {
                    plugin.getLogger().info("No empty shop found with coordinates " + coordinates + " to remove.");
                }
            }
        }).exceptionally(ex -> {
            plugin.getLogger().severe("Error removing empty shop for coords: " + coordinates + " - " + ex.getMessage());
            if (ex instanceof CompletionException) throw (CompletionException) ex;
            throw new CompletionException(ex);
        });
    }

    // =============================
    // ### SYSTEM ADMINISTRATION ###
    // =============================

    public static CompletableFuture<Void> rollback(String targetDateTime) {
        // Validate input
        return validateRollbackInput(targetDateTime).thenCompose(isValid -> {
            if (!isValid) {
                // If validation failed, complete with null or an appropriate exception immediately.
                return CompletableFuture.completedFuture(null); // Or CompletableFuture.failedFuture(new RuntimeException("Validation failed"));
            }
            
            final String targetDateTimeUTC;
            try {
                targetDateTimeUTC = TypeChecker.convertToUTC(targetDateTime);
            } catch (DateTimeParseException e) {
                plugin.getLogger().severe("Invalid targetDateTime format for validation: " + targetDateTime + " - " + e.getMessage());
                return CompletableFuture.completedFuture(null); // Format is invalid, no need to query DB
            }

            return getConnectionAsync().thenComposeAsync(conn -> { // New: chain directly
                if (conn == null) {
                    plugin.getLogger().severe("Rollback failed: Could not obtain database connection.");
                    return CompletableFuture.failedFuture(new SQLException("Failed to obtain database connection for rollback."));
                }
                return CompletableFuture.runAsync(() -> {
                    try {
                        // Disable auto-commit to ensure transaction consistency
                        conn.setAutoCommit(false);

                        try {
                            // CRITICAL NOTE: The following balance update logic is FLAWED for transactional integrity.
                            // displayBalance, getPlayerBalanceChange, and setPlayerBalance are asynchronous operations
                            // that acquire their OWN connections from the pool. Their updates WILL NOT be part of
                            // the transaction controlled by 'conn'. A full rollback of balances requires these
                            // operations to be executed on the 'conn' itself, likely via new synchronous helper methods
                            // that accept a Connection object or a complete redesign of this rollback mechanism.
                            // The current implementation will likely lead to inconsistent data if an error occurs
                            // during the balance update phase, as only operations directly on 'conn' (like deletes)
                            // would be rolled back.
                            //
                            // TO PROPERLY FIX: Consider creating synchronous versions of balance modification methods
                            // that take an existing Connection as a parameter, e.g.:
                            //   private static double getBalanceSync(Connection conn, String uuid) throws SQLException { ... }
                            //   private static void updateBalanceSync(Connection conn, String uuid, double newBalance, double newChange) throws SQLException { ... }
                            // Then, use these synchronous methods here within the transaction managed by 'conn'.

                            // 1. Get all successful transactions after the target datetime
                            String getTransactionsSQL =
                                    "SELECT * FROM Transactions " +
                                    "WHERE TransactionDatetime > ? AND Passed = 1 " +
                                    "ORDER BY TransactionDatetime DESC LIMIT ? OFFSET ?"; // Add LIMIT and OFFSET

                            int batchSize = 100; // Define a suitable batch size
                            AtomicInteger offset = new AtomicInteger(0); // Use AtomicInteger for offset
                            boolean hasMoreRows = true;

                            while (hasMoreRows) {
                                try (PreparedStatement pstmt = conn.prepareStatement(getTransactionsSQL)) {
                                    pstmt.setString(1, targetDateTimeUTC); // Set target datetime
                                    pstmt.setInt(2, batchSize); // Set limit
                                    pstmt.setInt(3, offset.get()); // Use offset.get() to get the current value
                                    ResultSet rs = pstmt.executeQuery();

                                    int processedRows = 0; // Count processed rows in this batch

                                    // 2. Reverse each transaction in the batch
                                    while (rs.next() && processedRows < batchSize) {
                                        String source = rs.getString("Source");
                                        String destination = rs.getString("Destination");
                                        float amount = rs.getFloat("Amount");

                                        // Update source balance if exists
                                        if (source != null) {
                                            // Use synchronous methods with the transaction's connection
                                            double currentBalance = displayBalanceSync(conn, source);
                                            double currentChange = getPlayerBalanceChangeSync(conn, source);
                                            double newBalance = currentBalance + amount;
                                            double newChange = currentChange + amount;
                                            setPlayerBalanceSync(conn, source, newBalance, newChange);
                                        }

                                        // Update destination balance if exists
                                        if (destination != null) {
                                            // Use synchronous methods with the transaction's connection
                                            double currentBalance = displayBalanceSync(conn, destination);
                                            double currentChange = getPlayerBalanceChangeSync(conn, destination);
                                            double newBalance = currentBalance - amount;
                                            double newChange = currentChange - amount;
                                            setPlayerBalanceSync(conn, destination, newBalance, newChange);
                                        }

                                        processedRows++;
                                    }

                                    // Check if we processed fewer rows than the batch size
                                    if (processedRows < batchSize) {
                                        hasMoreRows = false; // No more rows to process
                                    }
                                }
                                offset.addAndGet(batchSize); // Increment the offset for the next batch
                            }

                            // 3. Delete transactions after target datetime
                            String deleteTransactionsSQL = "DELETE FROM Transactions WHERE TransactionDatetime > ?";
                            try (PreparedStatement pstmt = conn.prepareStatement(deleteTransactionsSQL)) {
                                pstmt.setString(1, targetDateTimeUTC);
                                int rowsDeleted = pstmt.executeUpdate(); // Store the number of deleted rows
                                plugin.getLogger().info(rowsDeleted + " transactions deleted after " + targetDateTime); // Log the count
                            }

                            // 4. Delete autopays created after target datetime
                            String deleteAutopaysSQL = "DELETE FROM Autopays WHERE AutopayDatetime > ?";
                            try (PreparedStatement pstmt = conn.prepareStatement(deleteAutopaysSQL)) {
                                pstmt.setString(1, targetDateTimeUTC);
                                pstmt.executeUpdate();
                            }

                            // 5. Reset account creation dates that are after target datetime
                            String resetAccountsSQL =
                                    "UPDATE PlayerAccounts SET AccountDatetime = ? " +
                                    "WHERE AccountDatetime > ?";
                            try (PreparedStatement pstmt = conn.prepareStatement(resetAccountsSQL)) {
                                pstmt.setString(1, targetDateTimeUTC);
                                pstmt.setString(2, targetDateTimeUTC);
                                pstmt.executeUpdate();
                            }

                            // If everything succeeded, commit the transaction
                            conn.commit();
                            plugin.getLogger().info("Successfully rolled back database to " + targetDateTime);

                        } catch (SQLException e) {
                            // If anything fails, roll back all changes
                            conn.rollback();
                            plugin.getLogger().severe("Error during rollback, changes reverted: " + e.getMessage());
                            throw e; // Rethrow to handle it at a higher level
                        }
                    } catch (SQLException e) {
                        plugin.getLogger().severe("Database rollback failed: " + e.getMessage());
                    } finally {
                        try {
                            conn.close();
                        } catch (SQLException e) {
                            plugin.getLogger().severe("Error closing connection: " + e.getMessage());
                        }
                    }
                }, executorService); // End of the inner runAsync for transactional logic
            }, executorService) // End of thenComposeAsync for connection handling
            .exceptionally(ex -> {
                plugin.getLogger().severe("Comprehensive error during rollback operation: " + ex.getMessage());
                // Ensure that we still return CompletableFuture<Void>
                return null; // Or CompletableFuture.failedFuture(ex) if callers should strongly react
            });
        });
    }

    private static CompletableFuture<Boolean> validateRollbackInput(String targetDateTime) {
        // Validate targetDateTime
        final String targetDateTimeUTC;
        try {
            targetDateTimeUTC = TypeChecker.convertToUTC(targetDateTime);
        } catch (DateTimeParseException e) {
            plugin.getLogger().severe("Invalid targetDateTime format for validation: " + targetDateTime + " - " + e.getMessage());
            return CompletableFuture.completedFuture(false); // Format is invalid, no need to query DB
        }

        String checkTransactionsSQL = "SELECT COUNT(*) FROM Transactions WHERE TransactionDatetime > ?";
        return executeQueryAsync(conn -> { // Connection is managed by executeQueryAsync
            try (PreparedStatement pstmt = conn.prepareStatement(checkTransactionsSQL)) {
                pstmt.setString(1, targetDateTimeUTC);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        boolean transactionsExist = rs.getInt(1) > 0;
                        if (!transactionsExist) {
                            plugin.getLogger().info("No transactions found to roll back after " + targetDateTime);
                        }
                        return transactionsExist;
                    }
                }
            }
            // If we reach here, something went wrong with SQL execution or ResultSet processing
            // executeQueryAsync should ideally handle logging the SQLException
            return false; // Default to false if there was an issue or no rows found
        }).exceptionally(ex -> {
            plugin.getLogger().severe("Error during transaction check for rollback validation: " + ex.getMessage());
            return false; // On exception, consider it not valid to proceed
        });
    }

    // =================================
    // ### DATA MIGRATION AND EXPORT ###
    // =================================

    public static CompletableFuture<Void> migrateToDatabase() {
        AtomicInteger failedCounter = new AtomicInteger(0);
        return CompletableFuture.runAsync(() -> {
            try {
                FileConfiguration balanceConfig = BalanceFile.get();
                ConfigurationSection playersSection = balanceConfig.getConfigurationSection("players");
                if (playersSection == null) {
                    plugin.getLogger().info("No player balances found. Migration complete.");
                    return;
                }

                int batchSize = 100; // Define a suitable batch size
                List<String> keys = new ArrayList<>(playersSection.getKeys(false));
                int totalKeys = keys.size();

                List<CompletableFuture<Void>> futures = new ArrayList<>(); // Collect futures for batch processing

                for (int i = 0; i < totalKeys; i++) {
                    String key = keys.get(i);
                    if (key.length() == 32) {
                        double balance = playersSection.getDouble(key + ".balance");
                        double change = playersSection.getDouble(key + ".change");
                        String playerName = playersSection.getString(key + ".name");
                        String trimmedUuid = TypeChecker.trimUUID(key);

                        CompletableFuture<Void> future = accountExists(trimmedUuid).thenCompose(exists -> {
                            if (!exists) {
                                return addAccount(trimmedUuid, playerName, balance, change, result -> {
                                    // Handle the callback if needed
                                });
                            } else {
                                return setPlayerBalance(trimmedUuid, balance, change);
                            }
                        });

                        futures.add(future); // Add future to the list

                        // Commit in batches
                        if ((i + 1) % batchSize == 0 || i == totalKeys - 1) {
                            final int currentCount = i + 1;  // Create effectively final variable
                            CompletableFuture<Void> batchFuture = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
                            batchFuture.thenRun(() -> {
                                plugin.getLogger().info("Processed " + currentCount + " player accounts.");
                            });
                            futures.clear(); // Clear the list for the next batch
                        }
                    } else {
                        plugin.getLogger().warning("Invalid UUID for: " + key);
                        failedCounter.incrementAndGet();
                    }
                }

                plugin.getLogger().info("Migration to database completed.");
                if (failedCounter.get() != 0) {
                    plugin.getLogger().warning("Skipped " + failedCounter.get() + " accounts! Due to incorrect UUID format, check balance.yml");
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Error during migration to database: " + e.getMessage());
            }
        }, executorService); // Use the executorService for async execution
    }

    public static CompletableFuture<Void> migrateToBalanceFile() {
        String sqlCount = "SELECT COUNT(*) AS playerCount FROM PlayerAccounts";
        String sqlFetch = "SELECT UUID, PlayerName, Balance, BalChange FROM PlayerAccounts LIMIT ? OFFSET ?";
        int batchSize = 100;

        return executeQueryAsync(conn -> { // Connection for counting players
            try (PreparedStatement pstmtCount = conn.prepareStatement(sqlCount);
                 ResultSet rsCount = pstmtCount.executeQuery()) {
                if (rsCount.next()) {
                    return rsCount.getInt("playerCount");
                }
            }
            return 0; // No players found or error
        }).thenCompose(playerCount -> {
            if (playerCount == 0) {
                plugin.getLogger().info("No players in database to migrate to balance.yml.");
                return CompletableFuture.completedFuture(null);
            }

            BalanceFile.setup();
            FileConfiguration balanceConfig = BalanceFile.get(); // Get it once
            balanceConfig.options().copyDefaults(true);
            balanceConfig.set("format", "uuid");

            List<CompletableFuture<Void>> allBatchFutures = new ArrayList<>();
            for (int offset = 0; offset < playerCount; offset += batchSize) {
                final int currentOffset = offset;
                CompletableFuture<Void> batchFuture = executeQueryAsync(conn -> { // New connection per batch query
                    List<Map<String, Object>> batchData = new ArrayList<>();
                    try (PreparedStatement pstmtFetch = conn.prepareStatement(sqlFetch)) {
                        pstmtFetch.setInt(1, batchSize);
                        pstmtFetch.setInt(2, currentOffset);
                        try (ResultSet rs = pstmtFetch.executeQuery()) {
                            while (rs.next()) {
                                Map<String, Object> playerData = new HashMap<>();
                                playerData.put("UUID", rs.getString("UUID"));
                                playerData.put("PlayerName", rs.getString("PlayerName"));
                                playerData.put("Balance", rs.getDouble("Balance"));
                                playerData.put("BalChange", rs.getDouble("BalChange"));
                                batchData.add(playerData);
                            }
                        }
                    }
                    return batchData;
                }).thenAcceptAsync(batchData -> {
                    // This part is CPU-bound (config manipulation) and can run on executorService
                    for (Map<String, Object> playerData : batchData) {
                        String uuid = (String) playerData.get("UUID");
                        String playerName = (String) playerData.get("PlayerName");
                        double balance = (Double) playerData.get("Balance");
                        double change = (Double) playerData.get("BalChange");
                        String trimmedUuid = TypeChecker.trimUUID(uuid);

                        if (uuid != null && playerName != null && balance >= 0) {
                            balanceConfig.set("players." + trimmedUuid + ".name", playerName);
                            balanceConfig.set("players." + trimmedUuid + ".balance", balance);
                            balanceConfig.set("players." + trimmedUuid + ".change", change);
                        } else {
                            plugin.getLogger().warning("Invalid data for UUID: " + uuid + " during migration to balance.yml");
                        }
                    }
                    plugin.getLogger().info("Processed batch for migration: offset " + currentOffset);
                }, executorService); // Ensure config updates happen on a suitable thread if they are not thread-safe by default
                allBatchFutures.add(batchFuture);
            }

            return CompletableFuture.allOf(allBatchFutures.toArray(new CompletableFuture[0]))
                    .thenRun(() -> {
                        BalanceFile.save(); // Save config after all batches are processed
                        plugin.getLogger().info("Successfully migrated " + playerCount + " players to balance.yml");
                    });
        }).exceptionally(ex -> {
            plugin.getLogger().severe("Error during migration to balance.yml: " + ex.getMessage());
            return null; // Complete exceptionally
        });
    }

    public static CompletableFuture<Void> exportDatabase() {
        String sqlAccounts = "SELECT * FROM PlayerAccounts";
        String sqlTransactions = "SELECT * FROM Transactions";
        String sqlAutopays = "SELECT * FROM Autopays";
        String sqlEmptyShops = "SELECT * FROM EmptyShops";
        String csvFilePath = "QE_DatabaseExport.csv";
        int batchSize = 100;

        return getConnectionAsync().thenComposeAsync(conn -> {
            if (conn == null) {
                plugin.getLogger().severe("Database export failed: Could not obtain database connection.");
                return CompletableFuture.failedFuture(new SQLException("Failed to obtain database connection for export."));
            }

            CompletableFuture<Void> fileWritingOperations = CompletableFuture.runAsync(() -> {
                try (FileWriter csvWriter = new FileWriter(csvFilePath)) {
                    // These .join() calls will block the current virtual thread, which is acceptable
                    // as they ensure sequential writing to the same FileWriter.
                    exportTableToCSV(conn, sqlAccounts, "PlayerAccounts Table", csvWriter, batchSize).join();
                    exportTableToCSV(conn, sqlTransactions, "Transactions Table", csvWriter, batchSize).join();
                    exportTableToCSV(conn, sqlAutopays, "Autopays Table", csvWriter, batchSize).join();
                    exportTableToCSV(conn, sqlEmptyShops, "EmptyShops Table", csvWriter, batchSize).join();

                    plugin.getLogger().info("Database exported to " + csvFilePath + " successfully.");
                } catch (IOException e) {
                    plugin.getLogger().severe("Error writing CSV file during export: " + e.getMessage());
                    throw new CompletionException("CSV file writing error", e);
                } catch (CompletionException e) { // Catches exceptions from join() if an exportTableToCSV sub-task fails
                    plugin.getLogger().severe("Error during a table export sub-task: " + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage()));
                    throw e; // Re-throw to fail fileWritingOperations future
                } catch (Exception e) { // Catch any other unexpected errors during the export logic
                    plugin.getLogger().severe("Unexpected error during CSV export logic: " + e.getMessage());
                    throw new CompletionException("Unexpected CSV export error", e);
                }
            }, executorService);

            // Ensure the connection is closed after fileWritingOperations completes, regardless of success or failure.
            // The whenComplete block itself will run on the executorService.
            return fileWritingOperations.whenCompleteAsync((result, ex) -> {
                try {
                    if (conn != null && !conn.isClosed()) {
                        conn.close();
                    }
                } catch (SQLException sqlEx) {
                    plugin.getLogger().warning("Failed to close connection after export: " + sqlEx.getMessage());
                    if (ex == null) { // If fileWritingOperations succeeded, but closing conn failed, this is the new primary error.
                        throw new CompletionException("Failed to close connection after successful export", sqlEx);
                    }

                }
                // If 'ex' (from fileWritingOperations) is not null, it will propagate from whenComplete.
            }, executorService);

        }, executorService) // executorService for the thenComposeAsync stage after getConnectionAsync
        .exceptionally(ex -> {
            Throwable rootCause = ex;
            if (ex instanceof CompletionException && ex.getCause() != null) {
                rootCause = ex.getCause();
            }
            plugin.getLogger().severe("General error during database export operation: " + rootCause.getMessage());
            if (rootCause != ex) {
                // Log the stack trace to the console, as Logger doesn't have a standard severe with throwable
                rootCause.printStackTrace(); // This will print to standard error
            }

            if (ex instanceof CompletionException) {
                throw (CompletionException) ex;
            } else {
                throw new CompletionException("Database export failed due to an unexpected error", ex);
            }
        });
    }

    public static CompletableFuture<Void> exportTableToCSV(Connection conn, String sql, String tableName, FileWriter csvWriter, int batchSize) {
        return CompletableFuture.runAsync(() -> {
            try {
                int offset = 0;
                boolean hasMoreRows = true;

                while (hasMoreRows) {
                    try (PreparedStatement pstmt = conn.prepareStatement(sql + " LIMIT ? OFFSET ?")) {
                        pstmt.setInt(1, batchSize);
                        pstmt.setInt(2, offset);
                        ResultSet rs = pstmt.executeQuery();

                        // Write the result set to CSV
                        if (!rs.isBeforeFirst()) { // Check if the result set is empty
                            hasMoreRows = false; // No more rows to process
                        } else {
                            writeResultSetToCSV(rs, csvWriter); // This will write headers for each batch if not handled
                            offset += batchSize; // Increment the offset for the next batch
                        }
                    } catch (SQLException e) {
                        plugin.getLogger().severe("SQL error while exporting table '" + tableName + "' at offset " + offset + ": " + e.getMessage());
                        throw new CompletionException(e);
                    } catch (IOException e) {
                        plugin.getLogger().severe("IO error while writing to CSV for table '" + tableName + "' at offset " + offset + ": " + e.getMessage());
                        throw new CompletionException(e);
                    }
                    if (!hasMoreRows) {
                        break;
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Unexpected error while exporting table '" + tableName + "': " + e.getMessage());
                throw new CompletionException(e);
            }
        }, executorService);
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
