package net.derfla.quickeconomy.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.derfla.quickeconomy.Main;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;

/**
 * Shared database utilities, including connection pool management and
 * asynchronous query/update helpers with retry handling.
 */
public class Utility {

    static Plugin plugin = Main.getInstance();
    public static HikariDataSource dataSource;
    static final ExecutorService executorService = Main.getExecutorService();

    @FunctionalInterface
    interface SQLConsumer<T> {
        void accept(T t) throws SQLException;
    }

    @FunctionalInterface
    interface SQLFunction<T, R> {
        R apply(T t) throws SQLException;
    }

    /**
     * Run a read-only SQL operation asynchronously on a pooled connection.
     *
     * @param queryFunction function that accepts a connection and returns a result
     * @param <T>           the result type
     * @return a future that completes with the function's result
     */
    public static <T> CompletableFuture<T> executeQueryAsync(Utility.SQLFunction<Connection, T> queryFunction) {
        return RetryUtility.withRetry(() -> getConnectionAsync().thenCompose(conn -> {
            if (conn == null) {
                return CompletableFuture.failedFuture(new SQLException("Failed to obtain database connection."));
            }
            return CompletableFuture.supplyAsync(() -> {
                try (Connection autoCloseConn = conn) { // Use try-with-resources
                    return queryFunction.apply(autoCloseConn);
                } catch (SQLException e) {
                    plugin.getLogger().severe("SQL operation failed: " + e.getMessage());
                    throw new CompletionException(e);
                }
            }, executorService);
        }));
    }

    /**
     * Run a write/update SQL operation asynchronously on a pooled connection.
     *
     * @param updateAction consumer that performs the update using the provided connection
     * @return a future that completes when the action finishes
     */
    public static CompletableFuture<Void> executeUpdateAsync(Utility.SQLConsumer<Connection> updateAction) {
        return RetryUtility.withRetry(() -> getConnectionAsync().thenCompose(conn -> {
            if (conn == null) {
                return CompletableFuture.failedFuture(new SQLException("Failed to obtain database connection."));
            }
            return CompletableFuture.runAsync(() -> {
                try (Connection autoCloseConn = conn) { // Use try-with-resources
                    updateAction.accept(autoCloseConn);
                } catch (SQLException e) {
                    plugin.getLogger().severe("SQL update operation failed: " + e.getMessage());
                    throw new CompletionException(e);
                }
            }, executorService);
        }));
    }

    /**
     * Obtain a pooled connection asynchronously.
     *
     * @return a future with a {@link Connection} or {@code null} when unavailable
     */
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

    /**
     * Initialize the HikariCP connection pool using plugin configuration.
     */
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

            int poolSize = plugin.getConfig().getInt("database.poolSize");
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

    /**
     * Close the HikariCP connection pool.
     */
    public static void closePool() {
        if (dataSource != null) {
            dataSource.close();
            plugin.getLogger().info("Database connection pool closed.");
        }
    }

    /**
     * Shutdown the database executor service.
     */
    public static void shutdownExecutorService() {
        executorService.shutdown();
    }

    /**
     * Check for existence of a table in the current database/schema.
     *
     * @param tableName table to check
     * @return a future completing with {@code true} if the table exists
     */
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
}
