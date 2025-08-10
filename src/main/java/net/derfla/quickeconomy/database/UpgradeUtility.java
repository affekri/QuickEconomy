package net.derfla.quickeconomy.database;

import net.derfla.quickeconomy.Main;
import net.derfla.quickeconomy.util.TypeChecker;
import org.bukkit.plugin.Plugin;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static net.derfla.quickeconomy.database.Utility.executorService;

/**
 * Utilities for upgrading the QuickEconomy database schema across plugin versions.
 * <p>
 * Contains sequential upgrade steps and helpers to check or bump the version stored
 * in the plugin configuration.
 */
public class UpgradeUtility {

    static Plugin plugin = Main.getInstance();

    /**
     * Gets the version of the database. Using the 'database.version' property in the config file.
     * If the property doesn't exist it will assume the database version is 1.1 since that is the last version before the property was introduced.
     * @return Returns the database version in string format.
     */
    /**
     * Get the current database version from configuration.
     * Defaults to 1.1 for pre-versioned databases.
     *
     * @return version string
     */
    private static String getDatabaseVersion() {
        String databaseVersion;
        if (plugin.getConfig().isSet("database.version")) {
            databaseVersion = plugin.getConfig().getString("database.version");
        } else {
            databaseVersion = "1.1";
        }
        return databaseVersion;
    }

    /**
     * Check if upgrades are needed for the database.
     * @return Returns true if current database version is older than the latest introduced database version.
     */
    /**
     * Determine whether the database requires an upgrade.
     *
     * @return true if the configured version is older than the latest supported version
     */
    public static boolean requiresUpgrade() {
        String latestDatabaseVersion = "1.3";
        return TypeChecker.isNewerVersion(latestDatabaseVersion, getDatabaseVersion());
    }

    /**
     * Starts the upgrade process. Currently, it is only set up to upgrade to 1.2.
     * However, plan is to add future upgrades here as well to make skipping versions possible.
     * As this will upgrade to each new version sequentially.
     */
    /**
     * Run pending upgrades until the database is up-to-date.
     */
    public static void startUpgrades() {
        while (requiresUpgrade()) {
            plugin.getLogger().info("Starting database upgrade.");
            switch (getDatabaseVersion()) {
                case "1.1":
                    upgradeToV1o2().join();
                case "1.2":
                    upgradeToV1o3().join();
            }
        }
        plugin.getLogger().info("Database upgrade finished.");
    }

    /**
     * Upgrades the database to align with the new changes introduced in the 1.2 version.
     */
    /**
     * Upgrade schema and types to match 1.2 requirements.
     *
     * @return a future that completes when the 1.2 upgrade finishes
     */
    private static CompletableFuture<Void> upgradeToV1o2() {
        List<String> tableUpgradeQueries = new ArrayList<>();
        String upgradeAutopays = "ALTER TABLE Autopays MODIFY COLUMN Amount DECIMAL(19, 2); ";
        tableUpgradeQueries.add(upgradeAutopays);
        String upgradePlayerAccounts = "ALTER TABLE PlayerAccounts MODIFY COLUMN Balance DECIMAL(19, 2), MODIFY COLUMN BalChange DECIMAL(19, 2), MODIFY COLUMN AccountDatetime DATETIME NOT NULL;";
        tableUpgradeQueries.add(upgradePlayerAccounts);
        String upgareTransactions =  "ALTER TABLE Transactions MODIFY COLUMN NewSourceBalance DECIMAL(19, 2), MODIFY COLUMN NewDestinationBalance DECIMAL(19, 2), MODIFY COLUMN Amount DECIMAL(19, 2), MODIFY COLUMN TransactionDatetime DATETIME NOT NULL; ";
        tableUpgradeQueries.add(upgareTransactions);

        return Utility.getConnectionAsync().thenCompose(conn -> {
            if (conn == null) {
                plugin.getLogger().severe("Failed to get connection for 'upgrade to 1.2'. Upgrade will not be completed.");
                return CompletableFuture.failedFuture(new SQLException("Failed to obtain database connection for upgradeToV1o2."));
            }

            CompletableFuture<Void> allTablesFuture = CompletableFuture.completedFuture(null);

            for (String query : tableUpgradeQueries) {
                final String currentQuery = query;
                allTablesFuture = allTablesFuture.thenCompose(v ->
                        CompletableFuture.runAsync(() -> {
                            try (Statement statement = conn.createStatement()) {
                                statement.executeUpdate(currentQuery);
                                String tableName = currentQuery.substring(currentQuery.indexOf("TABLE ") + 6, currentQuery.indexOf(" MODIFY")).trim();
                                plugin.getLogger().info("Table " + tableName + " upgraded.");
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

            return allTablesFuture.whenComplete((res, ex) -> {
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
                plugin.getLogger().severe("Error during upgradeToV1o2 database operations: " + ex.getMessage());
            } else {
                plugin.getConfig().set("database.version", "1.2");
                plugin.saveConfig();
                plugin.getLogger().info("Database table upgrade process to 1.2 completed.");
            }
        });
    }

    /**
     * Upgrade the database to align with the changes implemented in the 1.3 version.
     */
    /**
     * Upgrade schema and data to match 1.3 requirements.
     *
     * @return a future that completes when the 1.3 upgrade finishes
     */
    private static CompletableFuture<Void> upgradeToV1o3() {
        List<String> tableUpgradeQueries = new ArrayList<>();
        tableUpgradeQueries.add("ALTER TABLE Transactions DROP FOREIGN KEY transactions_ibfk_1;");
        tableUpgradeQueries.add("ALTER TABLE Transactions DROP FOREIGN KEY transactions_ibfk_2;");

        tableUpgradeQueries.add("UPDATE Transactions SET Destination = 'Bank' WHERE Destination IS NULL;");
        tableUpgradeQueries.add("UPDATE Transactions SET Source = 'Bank' WHERE Source IS NULL;");

        return Utility.getConnectionAsync().thenCompose(conn -> {
            if (conn == null) {
                plugin.getLogger().severe("Failed to get connection for 'upgrade to 1.3'. Upgrade will not be completed.");
                return CompletableFuture.failedFuture(new SQLException("Failed to obtain database connection for upgradeToV1o3."));
            }

            CompletableFuture<Void> allUpgradesFuture = CompletableFuture.completedFuture(null);

            for (String query : tableUpgradeQueries) {
                final String currentQuery = query;
                allUpgradesFuture = allUpgradesFuture.thenCompose(v ->
                        CompletableFuture.runAsync(() -> {
                            try (Statement statement = conn.createStatement()) {
                                statement.executeUpdate(currentQuery);
                            } catch (SQLException e) {
                                if (currentQuery.startsWith("ALTER TABLE Transactions DROP FOREIGN KEY")) {
                                    plugin.getLogger().info("Skipping missing foreign key during upgradeToV1o3: " + e.getMessage());
                                } else {
                                    plugin.getLogger().severe("SQL Error when executing upgrade query!");
                                    throw new CompletionException(e);
                                }
                            }
                        }, executorService)
                );
            }
            return allUpgradesFuture.whenComplete((res, ex) -> {
                try {
                    if (conn != null && !conn.isClosed()) {
                        conn.close();
                    }
                } catch (SQLException e) {
                    plugin.getLogger().warning("Failed to close connection after upgradeTo1o3 operations: " + e.getMessage());
                }
            });
        }).whenComplete((result, ex) -> {
            if (ex != null) {
                plugin.getLogger().severe("Error during upgradeToV1o3 database operations: " + ex.getMessage());
            } else {
                plugin.getConfig().set("database.version", "1.3");
                plugin.saveConfig();
                plugin.getLogger().info("Database table upgrade process to 1.3 completed.");
            }
        });
    }
}
