package net.derfla.quickeconomy.database;

import java.sql.SQLException;
import java.sql.SQLTransientException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

/**
 * Utility class providing database operation retry logic with exponential backoff.
 * This class wraps database operations in retry mechanisms to handle transient failures
 * such as connection timeouts, deadlocks, and temporary resource unavailability.
 * 
 * <p>The retry logic is specifically designed for database operations that return
 * {@link CompletableFuture} objects, making it suitable for asynchronous database
 * operations throughout the QuickEconomy plugin.</p>
 * 
 * <p>Retry behavior:
 * <ul>
 *   <li>Maximum of 3 retry attempts</li>
 *   <li>Exponential backoff starting at 1 second</li>
 *   <li>Only retries on transient SQL exceptions</li>
 *   <li>Non-transient errors are immediately propagated</li>
 * </ul>
 * </p>
 * 
 * @author QuickEconomy
 * @version 1.0
 * @since 1.0
 * @see SQLException
 * @see SQLTransientException
 */
public class RetryUtility {
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;

    /**
     * Wraps a database operation with automatic retry logic for transient failures.
     * This method will retry the operation up to {@value #MAX_RETRIES} times if it encounters
     * transient SQL exceptions, with exponential backoff between attempts.
     * 
     * @param <T> the type of result returned by the database operation
     * @param operation a supplier that provides the CompletableFuture representing the database operation
     * @return a CompletableFuture that will complete with the operation result or fail with the last exception
     * @throws CompletionException if all retry attempts fail or a non-transient error occurs
     */
    public static <T> CompletableFuture<T> withRetry(Supplier<CompletableFuture<T>> operation) {
        return withRetryInternal(operation, 0);
    }

    /**
     * Internal recursive method that implements the retry logic with exponential backoff.
     * 
     * @param <T> the type of result returned by the database operation
     * @param operation a supplier that provides the CompletableFuture representing the database operation
     * @param retryCount the current retry attempt number (0-based)
     * @return a CompletableFuture that will complete with the operation result or initiate another retry
     */
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

    /**
     * Determines if an exception represents a transient error that can be retried.
     * This method checks for specific types of SQL exceptions and SQL states that
     * indicate temporary failures rather than permanent errors.
     * 
     * <p>Transient errors include:
     * <ul>
     *   <li>Connection errors (SQL state 08xxx)</li>
     *   <li>Transaction rollback errors (SQL state 40xxx)</li>
     *   <li>Insufficient resource errors (SQL state 53xxx)</li>
     *   <li>Any {@link SQLTransientException}</li>
     * </ul>
     * </p>
     * 
     * @param ex the exception to evaluate
     * @return {@code true} if the exception represents a transient error that can be retried,
     *         {@code false} otherwise
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
    
    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private RetryUtility() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}
