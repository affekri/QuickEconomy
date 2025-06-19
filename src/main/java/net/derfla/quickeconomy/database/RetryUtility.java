package net.derfla.quickeconomy.database;

import java.sql.SQLException;
import java.sql.SQLTransientException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

public class RetryUtility {
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;

    public static <T> CompletableFuture<T> withRetry(Supplier<CompletableFuture<T>> operation) {
        return withRetryInternal(operation, 0);
    }

    private static <T> CompletableFuture<T> withRetryInternal(Supplier<CompletableFuture<T>> operation, int retryCount) {
        CompletableFuture<T> future = operation.get();
        return future.handle((result, ex) -> {
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
