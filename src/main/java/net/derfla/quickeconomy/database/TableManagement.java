package net.derfla.quickeconomy.database;

import net.derfla.quickeconomy.Main;
import net.derfla.quickeconomy.util.TypeChecker;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static net.derfla.quickeconomy.database.Utility.executorService;

public class TableManagement {

    static Plugin plugin = Main.getInstance();

    public static CompletableFuture<Void> createTables() {
        return Utility.getConnectionAsync().thenCompose(conn -> {
            if (conn == null) {
                plugin.getLogger().severe("Failed to get connection for createTables. Tables will not be created.");
                return CompletableFuture.failedFuture(new SQLException("Failed to obtain database connection for createTables."));
            }
            List<String> tableCreationQueries = new ArrayList<>();

            String PlayerAccounts = "CREATE TABLE IF NOT EXISTS PlayerAccounts ("
                    + "  UUID char(32) NOT NULL,"
                    + "  AccountDatetime varchar(23) NOT NULL,"
                    + "  PlayerName varchar(16) NOT NULL,"
                    + "  Balance DECIMAL(19, 2) NOT NULL DEFAULT 0,"
                    + "  BalChange DECIMAL(19, 2) NOT NULL DEFAULT 0,"
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
                    + "  NewSourceBalance DECIMAL(19, 2),"
                    + "  NewDestinationBalance DECIMAL(19, 2),"
                    + "  Amount DECIMAL(19, 2) NOT NULL,"
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
                    + "  Amount DECIMAL(19, 2) NOT NULL,"
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

        return Utility.executeQueryAsync(conn -> { // Manages conn for the check
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
                return Utility.executeUpdateAsync(conn2 -> { // Manages conn2 for the creation
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

    public static CompletableFuture<Void> createTransactionsView(@NotNull String uuid) {
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
                + "ORDER BY t.TransactionDatetime DESC;";
        String[] params = {trimmedUuid, trimmedUuid};
        return createViewInternal(viewName, databaseName, sql, params, "UUID: " + untrimmedUuid);
    }

    static CompletableFuture<Void> createEmptyShopsView(@NotNull String uuid) {
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
}
