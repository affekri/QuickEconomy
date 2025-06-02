package net.derfla.quickeconomy.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.derfla.quickeconomy.Main;
import net.derfla.quickeconomy.database.Utility;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLTransientException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

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
     * Executes a query on the database and returns the result.
     * 
     * @param queryFunction The function to execute on the database.
     * @return A CompletableFuture that resolves to the result of the query.
     */
    public static <T> CompletableFuture<T> executeQueryAsync(Utility.SQLFunction<Connection, T> queryFunction) {
        return Utility.withRetry(() -> getConnectionAsync().thenCompose(conn -> {
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
     * Executes an update on the database.
     * 
     * @param updateAction The function to execute on the database.
     * @return A CompletableFuture that completes when the update has been executed.
     */
    public static CompletableFuture<Void> executeUpdateAsync(Utility.SQLConsumer<Connection> updateAction) {
        return Utility.withRetry(() -> getConnectionAsync().thenCompose(conn -> {
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
     * Gets a connection from the database connection pool.
     * 
     * @return A CompletableFuture that resolves to a connection from the database connection pool.
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
     * Connects to the database using HikariCP. Supports MySQL and SQLite.
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

    /**
     * Closes the database connection pool.
     */
    public static void closePool() {
        if (dataSource != null) {
            dataSource.close();
            plugin.getLogger().info("Database connection pool closed.");
        }
    }

    /**
     * Shuts down the executor service.
     */
    public static void shutdownExecutorService() {
        executorService.shutdown();
    }

    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;

    /**
     * Retries an operation if it fails due to a transient error.
     * 
     * @param operation The operation to retry.
     * @return A CompletableFuture that resolves to the result of the operation.
     */
    public static <T> CompletableFuture<T> withRetry(Supplier<CompletableFuture<T>> operation) {
        return withRetryInternal(operation, 0);
    }

    /**
     * Retries an operation if it fails due to a transient error.
     * 
     * @param operation The operation to retry.
     * @param retryCount The number of times the operation has been retried.
     * @return A CompletableFuture that resolves to the result of the operation.
     */
    private static <T> CompletableFuture<T> withRetryInternal(Supplier<CompletableFuture<T>> operation, int retryCount) {
        CompletableFuture<T> future = operation.get();
        return future.<CompletableFuture<T>>handle((result, ex) -> {
            if (ex != null && isTransientError(ex) && retryCount < MAX_RETRIES) {
                try {
                    Thread.sleep(RETRY_DELAY_MS * (retryCount + 1)); // Exponential backoff
                    return withRetryInternal(operation, retryCount + 1);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new CompletionException(ie);
                }
            }
            if (ex != null) {
                if (ex instanceof CompletionException) {
                    throw (CompletionException) ex;
                }
                throw new CompletionException(ex);
            }
            return CompletableFuture.completedFuture(result);
        }).thenCompose(f -> f);
    }

    /**
     * Checks if an error is transient.
     * 
     * @param ex The error to check.
     * @return True if the error is transient, false otherwise.
     */
    private static boolean isTransientError(Throwable ex) {
        if (ex instanceof SQLException) {
            SQLException sqlEx = (SQLException) ex;
            // Check for transient SQL exceptions
            if (sqlEx instanceof SQLTransientException) {
                return true;
            }
            // Check SQL state for transient errors
            String sqlState = sqlEx.getSQLState();
            if (sqlState != null) {
                // Common transient error states
                return sqlState.startsWith("08") || // Connection errors
                       sqlState.startsWith("40") || // Transaction errors
                       sqlState.startsWith("53");   // Insufficient resources
            }
        }
        return false;
    }

}
