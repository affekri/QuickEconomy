package net.derfla.quickeconomy.database;

import net.derfla.quickeconomy.Main;
import net.derfla.quickeconomy.file.BalanceFile;
import net.derfla.quickeconomy.util.TypeChecker;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.FileWriter;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;

import static net.derfla.quickeconomy.database.AccountManagement.addAccount;
import static net.derfla.quickeconomy.database.Utility.executorService;

public class Migration {

    static Plugin plugin = Main.getInstance();

    /**
     * Migrates player balances from the balance.yml file to the database.
     * 
     * @return A CompletableFuture that completes when the migration is complete.
     */
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

                        CompletableFuture<Void> future = AccountManagement.accountExists(trimmedUuid).thenCompose(exists -> {
                            if (!exists) {
                                return addAccount(trimmedUuid, playerName, balance, change, result -> {
                                    // Handle the callback if needed
                                });
                            } else {
                                return AccountManagement.setPlayerBalance(trimmedUuid, balance)
                                    .thenCompose(v -> AccountManagement.setPlayerBalanceChange(trimmedUuid, change));
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

    /**
     * Migrates player balances from the database to the balance.yml file.
     * 
     * @return A CompletableFuture that completes when the migration is complete.
     */
    public static CompletableFuture<Void> migrateToBalanceFile() {
        String sqlCount = "SELECT COUNT(*) AS playerCount FROM PlayerAccounts";
        String sqlFetch = "SELECT UUID, PlayerName, Balance, BalChange FROM PlayerAccounts LIMIT ? OFFSET ?";
        int batchSize = 100;

        return Utility.executeQueryAsync(conn -> { // Connection for counting players
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
                CompletableFuture<Void> batchFuture = Utility.executeQueryAsync(conn -> { // New connection per batch query
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

    /**
     * Exports the database to a CSV file.
     * 
     * @return A CompletableFuture that completes when the export is complete.
     */
    public static CompletableFuture<Void> exportDatabase() {
        String sqlAccounts = "SELECT * FROM PlayerAccounts";
        String sqlTransactions = "SELECT * FROM Transactions";
        String sqlAutopays = "SELECT * FROM Autopays";
        String sqlShops = "SELECT * FROM Shops";
        String csvFilePath = "QE_DatabaseExport.csv";
        int batchSize = 100;

        return Utility.withRetry(() -> Utility.getConnectionAsync().thenComposeAsync(conn -> {
            if (conn == null) {
                plugin.getLogger().severe("Database export failed: Could not obtain database connection.");
                return CompletableFuture.failedFuture(new SQLException("Failed to obtain database connection for export."));
            }

            try (Connection autoCloseConn = conn;
                 FileWriter csvWriter = new FileWriter(csvFilePath)) {

                CompletableFuture<Void> exportFuture = CompletableFuture.runAsync(() -> {
                    try {
                        exportTableToCSV(autoCloseConn, sqlAccounts, "PlayerAccounts Table", csvWriter, batchSize).join();
                        exportTableToCSV(autoCloseConn, sqlTransactions, "Transactions Table", csvWriter, batchSize).join();
                        exportTableToCSV(autoCloseConn, sqlAutopays, "Autopays Table", csvWriter, batchSize).join();
                        exportTableToCSV(autoCloseConn, sqlShops, "Shops Table", csvWriter, batchSize).join();

                        plugin.getLogger().info("Database exported to " + csvFilePath + " successfully.");
                    } catch (Exception e) {
                        plugin.getLogger().severe("Error during database export: " + e.getMessage());
                        throw new CompletionException(e);
                    }
                }, executorService);

                return exportFuture;
            } catch (IOException e) {
                plugin.getLogger().severe("Error creating CSV file: " + e.getMessage());
                return CompletableFuture.failedFuture(e);
            } catch (SQLException e) {
                plugin.getLogger().severe("Error with database connection during export: " + e.getMessage());
                return CompletableFuture.failedFuture(e);
            }
        }, executorService));
    }

    /**
     * Exports a database table to a CSV file.
     * 
     * @param conn The database connection.
     * @param sql The SQL query to execute.
     * @param tableName The name of the table to export.
     * @param csvWriter The FileWriter to write the CSV file.
     * @param batchSize The batch size for the export.
     * @return A CompletableFuture that completes when the export is complete.
     */
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

    /**
     * Writes a ResultSet to a CSV file.
     * 
     * @param rs The ResultSet to write.
     * @param csvWriter The FileWriter to write the CSV file.
     */
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
