package net.derfla.quickeconomy.util;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.derfla.quickeconomy.Main;
import net.derfla.quickeconomy.file.BalanceFile;

import net.derfla.quickeconomy.model.PlayerAccount;
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

import java.io.FileWriter;
import java.io.IOException;
import java.util.function.Consumer;

public class DatabaseManager {

    static Plugin plugin = Main.getInstance();
    public static HikariDataSource dataSource;
    private static final ExecutorService executorService = Main.getExecutorService();
    
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

    private static CompletableFuture<Boolean> tableExists(@NotNull String tableName) {
        return getConnectionAsync().thenCompose(conn ->
                CompletableFuture.supplyAsync(() -> {
                    try (PreparedStatement pstmt = conn.prepareStatement(
                            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = ?"
                    )) {
                        pstmt.setString(1, tableName);
                        try (ResultSet rs = pstmt.executeQuery()) {
                            return rs.next() && rs.getInt(1) > 0;
                        }
                    } catch (SQLException e) {
                        plugin.getLogger().severe("Error checking table " + tableName + ": " + e.getMessage());
                        return false;
                    } finally {
                        try {
                            conn.close();
                        } catch (SQLException ignored) {}
                    }
                }, executorService)
        );
    }

    public static CompletableFuture<Void> createTables() {
        return getConnectionAsync().thenAccept(conn -> {
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
                    + "  PRIMARY KEY (TransactionID),"
                    + "  FOREIGN KEY (Source) REFERENCES PlayerAccounts(UUID),"
                    + "  FOREIGN KEY (Destination) REFERENCES PlayerAccounts(UUID)"
                    + ");";
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
                    + "  PRIMARY KEY (AutopayID),"
                    + "  FOREIGN KEY (Source) REFERENCES PlayerAccounts(UUID),"
                    + "  FOREIGN KEY (Destination) REFERENCES PlayerAccounts(UUID)"
                    + ");";
            tableCreationQueries.add(Autopays);

            String EmptyShops = "CREATE TABLE IF NOT EXISTS EmptyShops ("
                    + "  Coordinates varchar(32) NOT NULL,"
                    + "  Owner1 char(32),"
                    + "  Owner2 char(32),"
                    + "  PRIMARY KEY (Coordinates)"
                    + ");";
            tableCreationQueries.add(EmptyShops);

            for (String query : tableCreationQueries) {
                // Extract table name from query using the word after "EXISTS"
                final String extractedTableName = query.substring(query.indexOf("EXISTS") + 7).trim();
                final String tableName = extractedTableName.substring(0, extractedTableName.indexOf(" "));
                             
                tableExists(tableName).thenAccept(exists -> {
                    if (!exists) {
                        try (Statement statement = conn.createStatement()) {
                            statement.executeUpdate(query);
                            plugin.getLogger().info("Table created: " + tableName);
                        } catch (SQLException e) {
                            plugin.getLogger().severe("Error creating table: " + tableName + " " + e.getMessage());
                        }
                    }
                });
            }
        });
    }

    public static CompletableFuture<Void> addAccount(@NotNull String uuid, @NotNull String playerName, double balance, double change, Consumer<Void> callback) {
        return CompletableFuture.runAsync(() -> {
            String trimmedUuid = TypeChecker.trimUUID(uuid);
            Instant currentTime = Instant.now();
            String currentTimeString = TypeChecker.convertToUTC(currentTime.atZone(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

            accountExists(trimmedUuid).thenCompose(exists -> {
                if (exists) {
                    plugin.getLogger().info("Account already exists for player with UUID: " + trimmedUuid);
                    return CompletableFuture.completedFuture(null);
                } else {
                    String insertSql = "INSERT INTO PlayerAccounts (UUID, AccountDatetime, PlayerName, Balance, BalChange) "
                            + "VALUES (?, ?, ?, ?, ?)";
                    return getConnectionAsync().thenAccept(conn -> {
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
                        } catch (SQLException e) {
                            plugin.getLogger().severe("Error adding player account: " + e.getMessage());
                        }
                    });
                }
            }).thenRun(() -> createTransactionsView(trimmedUuid)); // Ensure createTransactionsView is called after the account is added
        }, executorService); // Use the executorService for async execution
    }

    public static CompletableFuture<Boolean> insertEmptyShop(@NotNull String coordinates, @NotNull String owner1, String owner2) {
        String Owner1 = TypeChecker.trimUUID(owner1);
        final String Owner2 = !owner2.isEmpty() ? TypeChecker.trimUUID(owner2) : "";

        // Return a CompletableFuture
        return emptyShopExists(coordinates).thenCompose(exists -> {
            if (exists) {
                // Update the owners of the existing shop
                String updateSql = "UPDATE EmptyShops SET Owner1 = ?, Owner2 = ? WHERE Coordinates = ?";
                return getConnectionAsync().thenAccept(conn -> {
                    try (PreparedStatement pstmt = conn.prepareStatement(updateSql)) {
                        pstmt.setString(1, Owner1);
                        pstmt.setString(2, Owner2);
                        pstmt.setString(3, coordinates);
                        pstmt.executeUpdate();
                        plugin.getLogger().info("Empty shop at " + coordinates + " updated with new owners.");
                    } catch (SQLException e) {
                        plugin.getLogger().severe("Error updating empty shop: " + e.getMessage());
                    }
                }).thenRun(() -> {
                    createEmptyShopsView(owner1);
                    if (!owner2.isEmpty()) {
                        createEmptyShopsView(owner2);
                    }
                }).thenApply(v -> true); // Return true if an existing shop was updated
            } else {
                // Insert a new empty shop
                String insertSql = "INSERT INTO EmptyShops (Coordinates, Owner1, Owner2) VALUES (?, ?, ?)";
                return getConnectionAsync().thenAccept(conn -> {
                    try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
                        pstmt.setString(1, coordinates);
                        pstmt.setString(2, Owner1);
                        pstmt.setString(3, Owner2);
                        pstmt.executeUpdate();
                        plugin.getLogger().info("Empty shop registered at " + coordinates);
                    } catch (SQLException e) {
                        plugin.getLogger().severe("Error inserting empty shop: " + e.getMessage());
                    }
                }).thenRun(() -> {
                    createEmptyShopsView(owner1);
                    if (!owner2.isEmpty()) {
                        createEmptyShopsView(owner2);
                    }
                }).thenApply(v -> false); // Return false if a new shop was created
            }
        });
    }

    private static CompletableFuture<Boolean> emptyShopExists(@NotNull String coordinates) {
        String sql = "SELECT COUNT(*) FROM EmptyShops WHERE Coordinates = ?";
        // Use CompletableFuture to handle the connection asynchronously
        return getConnectionAsync().thenCompose(conn -> {
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, coordinates);
                return CompletableFuture.supplyAsync(() -> {
                    try (ResultSet rs = pstmt.executeQuery()) {
                        if (rs.next()) {
                            return rs.getInt(1) > 0; // Return true if the shop exists
                        }
                    } catch (SQLException e) {
                        plugin.getLogger().severe("Error checking for empty shop: " + e.getMessage());
                    }
                    return false; // Return false if the shop does not exist or an error occurred
                }, executorService);
            } catch (SQLException e) {
                plugin.getLogger().severe("Error preparing statement for empty shop check: " + e.getMessage());
                return CompletableFuture.completedFuture(false);
            }
        });
    }

    public static CompletableFuture<Void> executeTransaction(@NotNull String transactType, @NotNull String induce, String source,
                                          String destination, double amount, String transactionMessage) {
        return CompletableFuture.runAsync(() -> {
            String trimmedSource = TypeChecker.trimUUID(source);
            String trimmedDestination = TypeChecker.trimUUID(destination);
            Instant currentTime = Instant.now(); // Use Instant for UTC time

            // SQL to handle the transaction
            String sqlUpdateSource = "UPDATE PlayerAccounts SET Balance = Balance - ? WHERE UUID = ?";
            String sqlUpdateDestination = "UPDATE PlayerAccounts SET Balance = Balance + ? WHERE UUID = ?";
            String sqlInsertTransaction = "INSERT INTO Transactions (TransactionDatetime, TransactionType, Induce, Source, Destination, NewSourceBalance, NewDestinationBalance, Amount, Passed, TransactionMessage)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

            // Use getConnectionAsync() instead of getConnection()
            getConnectionAsync().thenAccept(conn -> {
                try {
                    conn.setAutoCommit(false);  // Begin transaction

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

                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC); // Move this line here

                    // Insert into Transactions table
                    try (PreparedStatement pstmtInsertTransaction = conn.prepareStatement(sqlInsertTransaction)) {
                        pstmtInsertTransaction.setString(1, TypeChecker.convertToUTC(formatter.format(currentTime))); // Updated to use convertToUTC
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
                    Balances.addPlayerBalanceChange(trimmedDestination, amount);
                } catch (SQLException e) {
                    if(trimmedDestination != null && trimmedSource != null) {
                        plugin.getLogger().severe("Error executing transaction from " + trimmedSource + " to " + trimmedDestination + ": " + e.getMessage());
                    } else if (trimmedSource != null) {
                        plugin.getLogger().severe("Error executing transaction from " + trimmedSource + ": " + e.getMessage());
                    } else if (trimmedDestination != null) {
                        plugin.getLogger().severe("Error executing transaction to " + trimmedDestination + ": " + e.getMessage());
                    } else {
                        plugin.getLogger().severe("Error executing transaction: " + e.getMessage());
                    }
                } finally {
                    try {
                        conn.close();
                    } catch (SQLException e) {
                        plugin.getLogger().severe("Error closing connection: " + e.getMessage());
                    }
                }
            });
        }, executorService); // Use the executorService for async execution
    }

    private static void createTransactionsView(@NotNull String uuid) {
        String trimmedUuid = TypeChecker.trimUUID(uuid);
        String untrimmedUuid = TypeChecker.untrimUUID(uuid);
        String viewName = "vw_Transactions_" + trimmedUuid;
        String databaseName = plugin.getConfig().getString("database.database");
        String checkSQL = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.VIEWS WHERE TABLE_NAME = ? AND TABLE_SCHEMA = ?";

        // Use getConnectionAsync() instead of getConnection()
        getConnectionAsync().thenAccept(conn -> {
            try (PreparedStatement preparedStatement = conn.prepareStatement(checkSQL)) {
                preparedStatement.setString(1, viewName);
                preparedStatement.setString(2, databaseName);

                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    if (resultSet.next() && resultSet.getInt(1) > 0) {
                        return; // Exit if the view for that player already exists
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Error checking for existing transaction view: " + e.getMessage());
                return;
            }

            // Create the view if it does not exist
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

            // Create the view asynchronously
            getConnectionAsync().thenAccept(conn2 -> {
                try (PreparedStatement createViewStmt = conn2.prepareStatement(sql)) {
                    createViewStmt.setString(1, trimmedUuid);
                    createViewStmt.setString(2, trimmedUuid);
                    createViewStmt.executeUpdate();
                    plugin.getLogger().info("Transaction view created for UUID: " + untrimmedUuid);
                } catch (SQLException e) {
                    plugin.getLogger().severe("Error creating transaction view: " + e.getMessage());
                } finally {
                    try {
                        conn2.close();
                    } catch (SQLException e) {
                        plugin.getLogger().severe("Error closing connection: " + e.getMessage());
                    }
                }
            });
        });
    }

    public static CompletableFuture<String> displayTransactionsView(@NotNull String uuid, Boolean displayPassed, int page) {
        String trimmedUuid = TypeChecker.trimUUID(uuid);
        String untrimmedUuid = TypeChecker.untrimUUID(uuid);
        String viewName = "vw_Transactions_" + trimmedUuid;
        StringBuilder transactions = new StringBuilder();
        int pageSize = 10;
        // Calculate the offset for pagination
        int offset = (page - 1) * pageSize;
        String sql;

        if (displayPassed == null) {
            // Display all transactions
            sql = "SELECT TransactionDatetime, Amount, SourceUUID, DestinationUUID, SourcePlayerName, DestinationPlayerName, Message FROM " + viewName + " ORDER BY TransactionDatetime DESC LIMIT ? OFFSET ?";
        } else if (displayPassed) {
            // Display only passed transactions
            sql = "SELECT TransactionDatetime, Amount, SourceUUID, DestinationUUID, SourcePlayerName, DestinationPlayerName, Message FROM " + viewName + " WHERE Passed = 'Passed' ORDER BY TransactionDatetime DESC LIMIT ? OFFSET ?";
        } else {
            // Display only failed transactions
            sql = "SELECT TransactionDatetime, Amount, SourceUUID, DestinationUUID, SourcePlayerName, DestinationPlayerName, Message FROM " + viewName + " WHERE Passed = 'Failed' ORDER BY TransactionDatetime DESC LIMIT ? OFFSET ?";
        }

        // Return a CompletableFuture for the transaction display
        return getConnectionAsync().thenApply(conn -> {
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
            } catch (SQLException e) {
                plugin.getLogger().severe("SQL error viewing transactions for UUID " + untrimmedUuid + ": " + e.getMessage());
            } catch (Exception e) {
                plugin.getLogger().severe("Error viewing transactions for UUID " + untrimmedUuid + ": " + e.getMessage());
            } finally {
                try {
                    conn.close();
                } catch (SQLException e) {
                    plugin.getLogger().severe("Error closing connection: " + e.getMessage());
                }
            }
            return transactions.toString();
        });
    }

    private static void createEmptyShopsView(@NotNull String uuid) {
        String trimmedUuid = TypeChecker.trimUUID(uuid);
        String untrimmedUuid = TypeChecker.untrimUUID(uuid);
        String viewName = "vw_EmptyShops_" + trimmedUuid;
        String databaseName = plugin.getConfig().getString("database.database");
        String checkSQL = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.VIEWS WHERE TABLE_NAME = ? AND TABLE_SCHEMA = ?";

        // Use getConnectionAsync() instead of getConnection()
        getConnectionAsync().thenAccept(conn -> {
            try (PreparedStatement preparedStatement = conn.prepareStatement(checkSQL)) {
                preparedStatement.setString(1, viewName);
                preparedStatement.setString(2, databaseName);

                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    if (resultSet.next() && resultSet.getInt(1) > 0) {
                        return; // Exit if the view for that player already exists
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Error checking for existing empty shops view: " + e.getMessage());
                return;
            }

            // Create the view if it does not exist
            String sql = "CREATE VIEW " + viewName + " AS "
                    + "SELECT "
                    + "    e.Coordinates "
                    + "FROM EmptyShops e "
                    + "WHERE Owner1 = ? OR Owner2 = ?;";

            // Create the view asynchronously
            getConnectionAsync().thenAccept(conn2 -> {
                try (PreparedStatement createViewStmt = conn2.prepareStatement(sql)) {
                    createViewStmt.setString(1, trimmedUuid);
                    createViewStmt.setString(2, trimmedUuid);
                    createViewStmt.executeUpdate();
                    plugin.getLogger().info("Empty shops view created for UUID: " + untrimmedUuid);
                } catch (SQLException e) {
                    plugin.getLogger().severe("Error creating empty shops view: " + e.getMessage());
                } finally {
                    try {
                        conn2.close();
                    } catch (SQLException e) {
                        plugin.getLogger().severe("Error closing connection: " + e.getMessage());
                    }
                }
            });
        });
    }

    public static CompletableFuture<List<String>> displayEmptyShopsView(@NotNull String uuid) {
        String trimmedUuid = TypeChecker.trimUUID(uuid);
        String untrimmedUuid = TypeChecker.untrimUUID(uuid);
        String viewName = "vw_EmptyShops_" + trimmedUuid;
        List<String> emptyShops = new ArrayList<>();

            // Check if the view for that player exists
            String checkViewSQL = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.VIEWS WHERE TABLE_NAME = ? AND TABLE_SCHEMA = ?";
            String databaseName = plugin.getConfig().getString("database.database");

        // Use getConnectionAsync() instead of getConnection()
        return getConnectionAsync().thenCompose(conn -> {
            try (PreparedStatement pstmt = conn.prepareStatement(checkViewSQL)) {
                pstmt.setString(1, viewName);
                pstmt.setString(2, databaseName);

                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next() && rs.getInt(1) == 0) {
                        return CompletableFuture.completedFuture(emptyShops); // Return empty list if the view does not exist
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Error checking for empty shops view: " + e.getMessage());
                return CompletableFuture.completedFuture(emptyShops);
            }

                // If the view exists, proceed to retrieve the coordinates
                String sql = "SELECT Coordinates FROM " + viewName + ";";

            return getConnectionAsync().thenApply(conn2 -> {
                try (PreparedStatement pstmt = conn2.prepareStatement(sql);
                     ResultSet rs = pstmt.executeQuery()) {

                    while (rs.next()) {
                        String coordinates = rs.getString("Coordinates");
                        emptyShops.add(coordinates);
                    }
                } catch (SQLException e) {
                    plugin.getLogger().severe("Error retrieving empty shops for UUID " + untrimmedUuid + ": " + e.getMessage());
                } finally {
                    try {
                        conn2.close();
                    } catch (SQLException e) {
                        plugin.getLogger().severe("Error closing connection: " + e.getMessage());
                    }
                }

                return emptyShops; // Return the list of empty shops
            });
        });
    }

    public static CompletableFuture<Double> displayBalance(@NotNull String uuid) {
        String trimmedUuid = TypeChecker.trimUUID(uuid);
        String untrimmedUuid = TypeChecker.untrimUUID(uuid);
        String sql = "SELECT Balance FROM PlayerAccounts WHERE UUID = ?";

        // Use getConnectionAsync() instead of getConnection()
        return getConnectionAsync().thenApply(conn -> {
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, trimmedUuid);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getDouble("Balance"); // Return the balance
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Error viewing balance for UUID " + untrimmedUuid + ": " + e.getMessage());
            } finally {
                try {
                    conn.close();
                } catch (SQLException e) {
                    plugin.getLogger().severe("Error closing connection: " + e.getMessage());
                }
            }
            return 0.0; // Return 0 if no balance found or an error occurred
        });
    }

    public static CompletableFuture<List<Map<String, Object>>> viewAutopays(@NotNull String uuid) {
        String trimmedUuid = TypeChecker.trimUUID(uuid);
        String untrimmedUuid = TypeChecker.untrimUUID(uuid);
        String sql = "SELECT a.AutopayID, a.AutopayName, a.Amount, pa.PlayerName AS DestinationName, a.InverseFrequency, a.TimesLeft " +
                "FROM Autopays a " +
                "JOIN PlayerAccounts pa ON a.Destination = pa.UUID " +
                "WHERE a.Source = ? " +
                "ORDER BY a.AutopayDatetime DESC";

            List<Map<String, Object>> autopays = new ArrayList<>();

            // Use getConnectionAsync() instead of getConnection()
            return getConnectionAsync().thenApply(conn -> {
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
            } catch (SQLException e) {
                plugin.getLogger().severe("Error viewing autopays for UUID " + untrimmedUuid + ": " + e.getMessage());
            } finally {
                try {
                    conn.close();
                } catch (SQLException e) {
                    plugin.getLogger().severe("Error closing connection: " + e.getMessage());
                }
            }
            return autopays; // Return the list of autopays
        });
    }

    public static CompletableFuture<Void> addAutopay(String autopayName, @NotNull String uuid, @NotNull String destination,
                                  double amount, int inverseFrequency, int endsAfter) {
        return CompletableFuture.runAsync(() -> {
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
            
            Instant currentTime = Instant.now(); // Use Instant for UTC time
            String currentTimeUTC = TypeChecker.convertToUTC(currentTime.toString()); // Convert to UTC using TypeChecker

            String sql = "INSERT INTO Autopays (" +
                    "    Active, AutopayDatetime, AutopayName, Source, Destination," +
                    "    Amount, InverseFrequency, EndsAfter" +
                    ") VALUES (?, ?, ?, ?, ?, ?, ?, ?);";

            // Use getConnectionAsync() instead of getConnection()
            getConnectionAsync().thenAccept(conn -> {
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setInt(1, 1); // Active
                    pstmt.setString(2, currentTimeUTC); // AutopayDatetime converted to UTC
                    pstmt.setString(3, autopayName); // AutopayName
                    pstmt.setString(4, trimmedUuid); // Source
                    pstmt.setString(5, trimmedDestination); // Destination
                    pstmt.setDouble(6, amount); // Amount
                    pstmt.setInt(7, inverseFrequency); // InverseFrequency
                    pstmt.setInt(8, endsAfter); // EndsAfter

                    pstmt.executeUpdate();
                    plugin.getLogger().info("Autopay added successfully");
                } catch (SQLException e) {
                    plugin.getLogger().severe("Error adding autopay: " + e.getMessage());
                } finally {
                    try {
                        conn.close();
                    } catch (SQLException e) {
                        plugin.getLogger().severe("Error closing connection: " + e.getMessage());
                    }
                }
            });
        }, executorService); // Use the executorService for async execution
    }

    public static CompletableFuture<Void> stateChangeAutopay(boolean activeState, int autopayID, @NotNull String uuid) {
        return CompletableFuture.runAsync(() -> {
            String trimmedUuid = TypeChecker.trimUUID(uuid);
            String sql = "UPDATE Autopays SET Active = ? WHERE AutopayID = ? AND Source = ?;";

            // Use getConnectionAsync() instead of getConnection()
            getConnectionAsync().thenAccept(conn -> {
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
                } finally {
                    try {
                        conn.close();
                    } catch (SQLException e) {
                        plugin.getLogger().severe("Error closing connection: " + e.getMessage());
                    }
                }
            });
        }, executorService); // Use the executorService for async execution
    }

    public static CompletableFuture<Void> deleteAutopay(int autopayID, @NotNull String uuid) {
        return CompletableFuture.runAsync(() -> {
            String trimmedUuid = TypeChecker.trimUUID(uuid);
            String sql = "DELETE FROM Autopays WHERE AutopayID = ? AND Source = ?;";

            // Use getConnectionAsync() instead of getConnection()
            getConnectionAsync().thenAccept(conn -> {
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
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
                } finally {
                    try {
                        conn.close();
                    } catch (SQLException e) {
                        plugin.getLogger().severe("Error closing connection: " + e.getMessage());
                    }
                }
            });
        }, executorService); // Use the executorService for async execution
    }

    public static CompletableFuture<HashMap<String, PlayerAccount>> listAllAccounts() {
        return getConnectionAsync().thenApply(conn -> {
            String sql = "SELECT UUID, PlayerName, Balance, BalChange, AccountDatetime AS Created FROM PlayerAccounts ORDER BY PlayerName ASC";
            HashMap<String, PlayerAccount> accounts = new HashMap<>();

            try (PreparedStatement pstmt = conn.prepareStatement(sql);
                 ResultSet rs = pstmt.executeQuery()) {

                    while (rs.next()) {
                        String uuid = rs.getString("UUID");
                        String playerName = rs.getString("PlayerName");
                        double balance = rs.getDouble("Balance");
                        double change = rs.getDouble("BalChange");
                        String accountDatetimeUTC = rs.getString("Created"); // Get the UTC datetime
                        String accountDatetimeLocal = TypeChecker.convertToLocalTime(accountDatetimeUTC); // Convert to local time

                        accounts.put(uuid, new PlayerAccount(playerName, balance, change, accountDatetimeLocal));
                    }
                } catch (SQLException e) {
                    plugin.getLogger().severe("Error listing accounts: " + e.getMessage());
                } finally {
                    try {
                        conn.close();
                    } catch (SQLException e) {
                        plugin.getLogger().severe("Error closing connection: " + e.getMessage());
                    }
                }

            return accounts; // Return the list of accounts
        });
    }

    private static CompletableFuture<Boolean> validateRollbackInput(String targetDateTime) {
        // Validate targetDateTime
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Convert the targetDateTime to UTC format
                String targetDateTimeUTC = TypeChecker.convertToUTC(targetDateTime); // Use convertToUTC method

                // Check if there are any transactions to roll back
                String checkTransactionsSQL = "SELECT COUNT(*) FROM Transactions WHERE TransactionDatetime > ?";
                return getConnectionAsync().thenCompose(conn -> {
                    try (PreparedStatement pstmt = conn.prepareStatement(checkTransactionsSQL)) {
                        pstmt.setString(1, targetDateTimeUTC); // Use the converted UTC time
                        ResultSet rs = pstmt.executeQuery();

                        if (rs.next() && rs.getInt(1) == 0) {
                            plugin.getLogger().info("No transactions found to roll back after " + targetDateTime);
                            return CompletableFuture.completedFuture(false); // Exit the method if no transactions are found
                        }
                    } catch (SQLException e) {
                        plugin.getLogger().severe("Error checking for transactions: " + e.getMessage());
                        return CompletableFuture.completedFuture(false); // Exit the method if there's an error checking transactions
                    } finally {
                        try {
                            conn.close();
                        } catch (SQLException e) {
                            plugin.getLogger().severe("Error closing connection: " + e.getMessage());
                        }
                    }
                    return CompletableFuture.completedFuture(true); // Return true if transactions exist
                });
            } catch (DateTimeParseException e) {
                plugin.getLogger().severe("Invalid targetDateTime format: " + targetDateTime);
                return CompletableFuture.completedFuture(false); // Exit the method if the format is invalid
            }
        }).thenCompose(result -> result); // Flatten the CompletableFuture
    }

    public static CompletableFuture<Void> rollback(String targetDateTime) {
        // Validate input
        return validateRollbackInput(targetDateTime).thenCompose(isValid -> {
            if (!isValid) {
                return CompletableFuture.completedFuture(null);
            }

            // Return a CompletableFuture<Void>
            return CompletableFuture.runAsync(() -> {
                getConnectionAsync().thenAccept(conn -> {
                    try {
                        // Disable auto-commit to ensure transaction consistency
                        conn.setAutoCommit(false);

                        try {
                            // 1. Get all successful transactions after the target datetime
                            String getTransactionsSQL =
                                    "SELECT * FROM Transactions " +
                                    "WHERE TransactionDatetime > ? AND Passed = 1 " +
                                    "ORDER BY TransactionDatetime DESC LIMIT ? OFFSET ?"; // Add LIMIT and OFFSET

                            // Convert targetDateTime to UTC
                            String targetDateTimeUTC = TypeChecker.convertToUTC(targetDateTime);

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
                                        double amount = rs.getDouble("Amount");

                                        // Update source balance if exists
                                        if (source != null) {
                                            displayBalance(source).thenCombine(getPlayerBalanceChange(source), (balance, change) -> {
                                                Double newBalance = balance + amount; // Add amount to the balance
                                                Double newChange = change + amount;   // Add amount to the change
                                                setPlayerBalance(source, newBalance, newChange);
                                                return null; // Return null as we don't need a result
                                            });
                                        }

                                        // Update destination balance if exists
                                        if (destination != null) {
                                            displayBalance(destination).thenCombine(getPlayerBalanceChange(destination), (balance, change) -> {
                                                Double newBalance = balance - amount; // Subtract amount from the balance
                                                Double newChange = change - amount;   // Subtract amount from the change
                                                setPlayerBalance(destination, newBalance, newChange);
                                                return null; // Return null as we don't need a result
                                            });
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
                });
            }, executorService);
        });
    }

    public static CompletableFuture<Void> setPlayerBalance(@NotNull String uuid, double balance, double change) {
        return CompletableFuture.runAsync(() -> {
            String trimmedUuid = TypeChecker.trimUUID(uuid);
            String untrimmedUuid = TypeChecker.untrimUUID(uuid);
            String sql = "UPDATE PlayerAccounts SET Balance = ?, BalChange = ? WHERE UUID = ?;";

            // Use getConnectionAsync() instead of getConnection()
            getConnectionAsync().thenAccept(conn -> {
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
                } catch (SQLException e) {
                    plugin.getLogger().severe("Error updating balance for UUID " + untrimmedUuid + ": " + e.getMessage());
                } finally {
                    try {
                        conn.close();
                    } catch (SQLException e) {
                        plugin.getLogger().severe("Error closing connection: " + e.getMessage());
                    }
                }
            });
        }, executorService); // Use the executorService for async execution
    }

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
        String sql = "SELECT * FROM PlayerAccounts LIMIT ? OFFSET ?"; // Use LIMIT and OFFSET for pagination

        int batchSize = 100; // Define the batch size
        AtomicInteger offset = new AtomicInteger(0); // Use AtomicInteger for offset
        int[] playerCount = new int[1]; // Use an array to hold the count

        return CompletableFuture.runAsync(() -> {
            // Use getConnectionAsync() instead of getConnection()
            getConnectionAsync().thenAccept(conn -> {
                try (PreparedStatement pstmtCount = conn.prepareStatement(sqlCount);
                     ResultSet rsCount = pstmtCount.executeQuery()) {

                    if (rsCount.next()) {
                        playerCount[0] = rsCount.getInt("playerCount"); // Update the first element of the array
                    }

                    BalanceFile.setup();
                    BalanceFile.get().options().copyDefaults(true);
                    BalanceFile.save();

                    FileConfiguration balanceConfig = BalanceFile.get();
                    for (String key : balanceConfig.getKeys(false)) {
                        balanceConfig.set(key, null);
                    }

                    balanceConfig.set("format", "uuid");

                    // Loop to fetch and process records in batches
                    while (offset.get() < playerCount[0]) {
                        // Use getConnectionAsync() instead of getConnection()
                        getConnectionAsync().thenAccept(connBatch -> {
                            try {
                                try (PreparedStatement pstmt = connBatch.prepareStatement(sql)) {
                                    pstmt.setInt(1, batchSize); // Set the batch size
                                    pstmt.setInt(2, offset.get()); // Use offset.get() to get the current value
                                    ResultSet rs = pstmt.executeQuery();

                                    List<CompletableFuture<Void>> futures = new ArrayList<>(); // Collect futures for batch processing

                                    while (rs.next()) {
                                        String uuid = rs.getString("UUID");
                                        String trimmedUuid = TypeChecker.trimUUID(uuid);
                                        String untrimmedUuid = TypeChecker.untrimUUID(uuid);
                                        String playerName = rs.getString("PlayerName");
                                        double balance = rs.getDouble("Balance");
                                        double change = rs.getDouble("BalChange");

                                        // Validate data before writing
                                        if (uuid != null && playerName != null && balance >= 0) {
                                            futures.add(CompletableFuture.runAsync(() -> {
                                                balanceConfig.set("players." + trimmedUuid + ".name", playerName);
                                                balanceConfig.set("players." + trimmedUuid + ".balance", balance);
                                                balanceConfig.set("players." + trimmedUuid + ".change", change);
                                            }));
                                        } else {
                                            plugin.getLogger().warning("Invalid data for UUID: " + untrimmedUuid);
                                        }
                                    }

                                    // Wait for all futures in the batch to complete
                                    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenRun(() -> {
                                        offset.addAndGet(batchSize); // Increment the offset for the next batch
                                    }).join(); // Wait for the batch to complete before continuing
                                }
                            } catch (SQLException e) {
                                plugin.getLogger().severe("SQL error while processing batch: " + e.getMessage());
                            }
                        });
                    }

                    // Save the updated configuration to balance.yml
                    BalanceFile.save();
                    plugin.getLogger().info("Successfully migrated " + playerCount[0] + " players to balance.yml");

                } catch (SQLException e) {
                    plugin.getLogger().severe("Error migrating to balance.yml: " + e.getMessage());
                } finally {
                    try {
                        conn.close();
                    } catch (SQLException e) {
                        plugin.getLogger().severe("Error closing connection: " + e.getMessage());
                    }
                }
            });
        }, executorService); // Use the executorService for async execution
    }

    public static CompletableFuture<Boolean> accountExists(@NotNull String uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM PlayerAccounts WHERE UUID = ?";
            String trimmedUuid = TypeChecker.trimUUID(uuid);
            String untrimmedUuid = TypeChecker.untrimUUID(uuid);
            try {
                return getConnectionAsync().thenApply(conn -> {
                    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                        pstmt.setString(1, trimmedUuid);
                        try (ResultSet rs = pstmt.executeQuery()) {
                            if (rs.next()) {
                                return rs.getInt(1) > 0; // Return true if count is greater than 0
                            }
                        }
                    } catch (SQLException e) {
                        plugin.getLogger().severe("Error checking account existence for UUID " + untrimmedUuid + ": " + e.getMessage());
                    } finally {
                        try {
                            conn.close();
                        } catch (SQLException e) {
                            plugin.getLogger().severe("Error closing connection: " + e.getMessage());
                        }
                    }
                    return false;

                }).join(); // Wait for the CompletableFuture to complete
            } catch (Exception e) {
                plugin.getLogger().severe("Error checking account existence for UUID " + untrimmedUuid + ": " + e.getMessage());
                return false;
            }
        }, executorService); // Use the executorService for async execution
    }

    public static CompletableFuture<Void> updatePlayerName(String uuid, String playerName) {
        return CompletableFuture.runAsync(() -> {
            String sql = "UPDATE PlayerAccounts SET PlayerName = ? WHERE UUID = ?";
            String trimmedUuid = TypeChecker.trimUUID(uuid);
            String untrimmedUuid = TypeChecker.untrimUUID(uuid);

            // Use getConnectionAsync() instead of getConnection()
            getConnectionAsync().thenAccept(conn -> {
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, playerName);
                    pstmt.setString(2, trimmedUuid);
                    int rowsAffected = pstmt.executeUpdate();

                    if (rowsAffected > 0) {
                        plugin.getLogger().info("Player name updated successfully for UUID: " + untrimmedUuid);
                    } else {
                        plugin.getLogger().info("No account found for UUID: " + untrimmedUuid);
                    }
                } catch (SQLException e) {
                    plugin.getLogger().severe("Error updating player name for UUID " + untrimmedUuid + ": " + e.getMessage());
                } finally {
                    try {
                        conn.close();
                    } catch (SQLException e) {
                        plugin.getLogger().severe("Error closing connection: " + e.getMessage());
                    }
                }
            });
        }, executorService); // Use the executorService for async execution
    }

    public static CompletableFuture<Void> exportDatabase() {
        String sqlAccounts = "SELECT * FROM PlayerAccounts";
        String sqlTransactions = "SELECT * FROM Transactions";
        String sqlAutopays = "SELECT * FROM Autopays";
        String sqlEmptyShops = "SELECT * FROM EmptyShops";
        String csvFilePath = "QE_DatabaseExport.csv"; // Path to save the CSV file
        int batchSize = 100; // Define a suitable batch size

        return CompletableFuture.runAsync(() -> {
            // Use getConnectionAsync() instead of getConnection()
            getConnectionAsync().thenAccept(conn -> {
                try (FileWriter csvWriter = new FileWriter(csvFilePath)) {
                    exportTableToCSV(conn, sqlAccounts, "PlayerAccounts Table", csvWriter, batchSize).join();
                    exportTableToCSV(conn, sqlTransactions, "Transactions Table", csvWriter, batchSize).join();
                    exportTableToCSV(conn, sqlAutopays, "Autopays Table", csvWriter, batchSize).join();
                    exportTableToCSV(conn, sqlEmptyShops, "EmptyShops Table", csvWriter, batchSize).join();

                    plugin.getLogger().info("Database exported to " + csvFilePath + " successfully.");
                } catch (IOException e) {
                    plugin.getLogger().severe("Error writing CSV file: " + e.getMessage());
                } finally {
                    try {
                        conn.close();
                    } catch (SQLException e) {
                        plugin.getLogger().severe("Error closing connection: " + e.getMessage());
                    }
                }
            });
        }, executorService); // Use the executorService for async execution
    }

    public static CompletableFuture<Void> exportTableToCSV(Connection conn, String sql, String tableName, FileWriter csvWriter, int batchSize) {
        return CompletableFuture.runAsync(() -> {
            try {
                csvWriter.append(tableName + "\n");

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
                            break;
                        }

                        writeResultSetToCSV(rs, csvWriter);
                        offset += batchSize; // Increment the offset for the next batch
                        csvWriter.append("\n"); // Separate batches with a blank line
                    } catch (SQLException e) {
                        plugin.getLogger().severe("SQL error while exporting table '" + tableName + "' at offset " + offset + ": " + e.getMessage());
                        throw e; // Rethrow the exception to handle it at a higher level
                    } catch (IOException e) {
                        plugin.getLogger().severe("IO error while writing to CSV for table '" + tableName + "' at offset " + offset + ": " + e.getMessage());
                        throw e; // Rethrow the exception to handle it at a higher level
                    } catch (Exception e) {
                        plugin.getLogger().severe("Unexpected error while exporting table '" + tableName + "' at offset " + offset + ": " + e.getMessage());
                        throw e; // Rethrow the exception to handle it at a higher level
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Error getting connection for exporting table '" + tableName + "': " + e.getMessage());
            } catch (IOException e) {
                plugin.getLogger().severe("IO error while writing to CSV for table '" + tableName + "': " + e.getMessage());
            } finally {
                try {
                    conn.close();
                } catch (SQLException e) {
                    plugin.getLogger().severe("Error closing connection: " + e.getMessage());
                }
            }
        }, executorService); // Use the executorService for async execution
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

    public static CompletableFuture<Double> getPlayerBalanceChange(String uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String trimmedUUID = TypeChecker.trimUUID(uuid);
            Double[] change = {0.0}; // Use an array to hold the change value
            String sql = "SELECT BalChange FROM PlayerAccounts WHERE UUID = ?";

            // Use getConnectionAsync() instead of getConnection()
            return getConnectionAsync().thenApply(conn -> {
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, trimmedUUID);

                    try (ResultSet rs = pstmt.executeQuery()) {
                        if (rs.next()) {
                            change[0] = rs.getDouble("BalChange"); // Update the first element of the array
                        }
                    }
                } catch (SQLException e) {
                    plugin.getLogger().severe("Could not get change for player: " + e.getMessage());
                } finally {
                    try {
                        conn.close();
                    } catch (SQLException e) {
                        plugin.getLogger().severe("Error closing connection: " + e.getMessage());
                    }
                }
                return change[0]; // Return the value from the array
            }).join(); // Wait for the CompletableFuture to complete
        }, executorService); // Use the executorService for async execution
    }

    public static CompletableFuture<Void> setPlayerBalanceChange(@NotNull String uuid, double change) {
        return CompletableFuture.runAsync(() -> {
            String trimmedUuid = TypeChecker.trimUUID(uuid);
            String untrimmedUuid = TypeChecker.untrimUUID(uuid);
            String sql = "UPDATE PlayerAccounts SET BalChange = ? WHERE UUID = ?;";

            // Use getConnectionAsync() instead of getConnection()
            getConnectionAsync().thenAccept(conn -> {
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setDouble(1, change);
                    pstmt.setString(2, trimmedUuid);
                    pstmt.executeUpdate();
                } catch (SQLException e) {
                    plugin.getLogger().severe("Error updating change for UUID " + untrimmedUuid + ": " + e.getMessage());
                } finally {
                    try {
                        conn.close();
                    } catch (SQLException e) {
                        plugin.getLogger().severe("Error closing connection: " + e.getMessage());
                    }
                }
            });
        }, executorService); // Use the executorService for async execution
    }

    public static CompletableFuture<Void> removeEmptyShop(String coordinates) {
        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM EmptyShops WHERE Coordinates = ?;";
            // Use getConnectionAsync() instead of getConnection()
            getConnectionAsync().thenAccept(conn -> {
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, coordinates);
                    int rowsAffected = pstmt.executeUpdate();
                    if (rowsAffected > 0) {
                        plugin.getLogger().info("Empty shop at " + coordinates + " removed from the database.");
                    } else {
                        plugin.getLogger().info("No empty shop found with coordinates " + coordinates + " to remove.");
                    }
                } catch (SQLException e) {
                    plugin.getLogger().severe("Error removing empty shop: " + e.getMessage());
                } finally {
                    try {
                        conn.close();
                    } catch (SQLException e) {
                        plugin.getLogger().severe("Error closing connection: " + e.getMessage());
                    }
                }
            });
        }, executorService); // Use the executorService for async execution
    }

    public static CompletableFuture<String> getUUID(String playerName) {
        String sql = "SELECT UUID FROM PlayerAccounts WHERE PlayerName = ?";
        return CompletableFuture.supplyAsync(() -> {
            String[] uuidHolder = new String[1]; // Use an array to hold the UUID
            getConnectionAsync().thenAccept(conn -> {
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, playerName);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        if (rs.next()) {
                            uuidHolder[0] = rs.getString("UUID"); // Store the UUID
                        }
                    }
                } catch (SQLException e) {
                    plugin.getLogger().warning("Error retrieving UUID for player " + playerName + ": " + e.getMessage());
                } finally {
                    try {
                        conn.close();
                    } catch (SQLException e) {
                        plugin.getLogger().severe("Error closing connection: " + e.getMessage());
                    }
                }
            });
            return uuidHolder[0]; // Return the UUID or null if not found
        }, executorService); // Use the executorService for async execution
    }

}
