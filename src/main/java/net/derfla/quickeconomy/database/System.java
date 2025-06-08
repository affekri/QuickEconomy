package net.derfla.quickeconomy.database;

import net.derfla.quickeconomy.Main;
import net.derfla.quickeconomy.util.TypeChecker;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.format.DateTimeParseException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;

import static net.derfla.quickeconomy.database.AccountManagement.displayBalanceSync;
import static net.derfla.quickeconomy.database.Utility.executorService;

public class System {

    static Plugin plugin = Main.getInstance();

    public static CompletableFuture<Void> rollback(String targetDateTime) {
        // Validate input
        return validateRollbackInput(targetDateTime).thenCompose(isValid -> {
            if (!isValid) {
                return CompletableFuture.failedFuture(new IllegalStateException("Rollback validation failed. Input: " + targetDateTime));
            }

            final String targetDateTimeUTC;
            try {
                targetDateTimeUTC = TypeChecker.convertToUTC(targetDateTime);
            } catch (DateTimeParseException e) {
                plugin.getLogger().severe("Invalid targetDateTime format for rollback: " + targetDateTime + " - " + e.getMessage());
                return CompletableFuture.failedFuture(e);
            }

            return Utility.getConnectionAsync().thenComposeAsync(conn -> { // New: chain directly
                        if (conn == null) {
                            plugin.getLogger().severe("Rollback failed: Could not obtain database connection.");
                            return CompletableFuture.failedFuture(new SQLException("Failed to obtain database connection for rollback."));
                        }

                        CompletableFuture<Void> transactionLogicFuture = CompletableFuture.runAsync(() -> {
                            try {
                                conn.setAutoCommit(false); // Start transaction

                                // Inner try-catch for the actual transactional work and commit/rollback
                                try {
                                    // 1. Get all successful transactions after the target datetime (batched)
                                    String getTransactionsSQL =
                                            "SELECT * FROM Transactions " +
                                                    "WHERE TransactionDatetime > ? AND Passed = 1 " +
                                                    "ORDER BY TransactionDatetime DESC LIMIT ? OFFSET ?";
                                    int batchSize = 100;
                                    AtomicInteger offset = new AtomicInteger(0);
                                    boolean hasMoreRows = true;

                                    while (hasMoreRows) {
                                        try (PreparedStatement pstmt = conn.prepareStatement(getTransactionsSQL)) {
                                            pstmt.setString(1, targetDateTimeUTC);
                                            pstmt.setInt(2, batchSize);
                                            pstmt.setInt(3, offset.get());
                                            ResultSet rs = pstmt.executeQuery();
                                            int processedRows = 0;

                                            while (rs.next() && processedRows < batchSize) {
                                                String source = rs.getString("Source");
                                                String destination = rs.getString("Destination");
                                                double amount = rs.getDouble("Amount");

                                                if (source != null) {
                                                    double currentBalance = displayBalanceSync(conn, source);
                                                    double currentChange = getPlayerBalanceChangeSync(conn, source);
                                                    setPlayerBalanceSync(conn, source, currentBalance + amount, currentChange + amount);
                                                }
                                                if (destination != null) {
                                                    double currentBalance = displayBalanceSync(conn, destination);
                                                    double currentChange = getPlayerBalanceChangeSync(conn, destination);
                                                    setPlayerBalanceSync(conn, destination, currentBalance - amount, currentChange - amount);
                                                }
                                                processedRows++;
                                            }
                                            if (processedRows < batchSize) hasMoreRows = false;
                                        }
                                        offset.addAndGet(batchSize);
                                    }

                                    // 3. Delete transactions after target datetime
                                    String deleteTransactionsSQL = "DELETE FROM Transactions WHERE TransactionDatetime > ?";
                                    try (PreparedStatement pstmt = conn.prepareStatement(deleteTransactionsSQL)) {
                                        pstmt.setString(1, targetDateTimeUTC);
                                        int rowsDeleted = pstmt.executeUpdate();
                                        plugin.getLogger().info(rowsDeleted + " transactions deleted after " + targetDateTime);
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

                                    conn.commit(); // Commit transaction
                                    plugin.getLogger().info("Successfully rolled back database to " + targetDateTime);

                                } catch (SQLException e) {
                                    plugin.getLogger().warning("SQLException during rollback transaction, attempting to rollback...: " + e.getMessage());
                                    try {
                                        conn.rollback();
                                        plugin.getLogger().info("Transaction rolled back successfully due to error.");
                                    } catch (SQLException rbEx) {
                                        plugin.getLogger().severe("Failed to rollback transaction: " + rbEx.getMessage());
                                        e.addSuppressed(rbEx);
                                    }
                                    throw new CompletionException("Transactional logic failed during rollback", e);
                                }
                            } catch (SQLException e) { // Catches errors from conn.setAutoCommit(false) or other pre-transaction issues
                                plugin.getLogger().severe("SQLException preparing for rollback transaction (e.g., setAutoCommit): " + e.getMessage());
                                throw new CompletionException("Setup for rollback transaction failed", e);
                            }
                        }, executorService);

                        // Ensure connection is closed after transactionLogicFuture completes (successfully or exceptionally)
                        return transactionLogicFuture.whenCompleteAsync((result, ex) -> {
                            try {
                                if (conn != null && !conn.isClosed()) {
                                    conn.close();
                                }
                            } catch (SQLException sqlEx) {
                                plugin.getLogger().warning("Failed to close connection after rollback attempt: " + sqlEx.getMessage());
                                if (ex == null) { // transactionLogicFuture succeeded, but closing conn failed.
                                    throw new CompletionException("Failed to close connection after successful rollback logic execution", sqlEx);
                                }
                                // If transactionLogicFuture already failed (ex != null), its exception is primary.
                                // The sqlEx for closing is logged, and the original 'ex' will propagate.
                            }
                        }, executorService);

                    }, executorService) // For the thenComposeAsync stage after getConnectionAsync
                    .exceptionally(ex -> {
                        Throwable rootCause = ex;
                        if (ex instanceof CompletionException && ex.getCause() != null) {
                            rootCause = ex.getCause();
                        }
                        plugin.getLogger().severe("General error during rollback operation: " + rootCause.getMessage());
                        if (rootCause != ex) {
                            rootCause.printStackTrace();
                        }
                        // Ensure consistent exception type for the CompletableFuture<Void>
                        if (ex instanceof CompletionException) throw (CompletionException)ex;
                        throw new CompletionException("Rollback operation failed unexpectedly", ex);
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
        return Utility.executeQueryAsync(conn -> { // Connection is managed by executeQueryAsync
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
}
