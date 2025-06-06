package net.derfla.quickeconomy.database;

import net.derfla.quickeconomy.Main;
import net.derfla.quickeconomy.util.Balances;
import net.derfla.quickeconomy.util.TypeChecker;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;

public class TransactionManagement {

    static Plugin plugin = Main.getInstance();

    public static CompletableFuture<Void> executeTransaction(@NotNull String transactType, @NotNull String induce, String source,
                                                             String destination, double amount, String transactionMessage) {
        // Sort UUIDs to prevent deadlocks
        String trimmedSource = source != null ? TypeChecker.trimUUID(source) : null;
        String trimmedDestination = destination != null ? TypeChecker.trimUUID(destination) : null;

        Instant currentTime = Instant.now();
        String currentUTCTimeString = currentTime.atZone(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));

        return RetryUtility.withRetry(() -> Utility.executeUpdateAsync(conn -> {
            conn.setAutoCommit(false);
            conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

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
                if (trimmedDestination != null) Balances.addPlayerBalanceChange(trimmedDestination, amount);
            } catch (SQLException e) {
                conn.rollback();
                if(trimmedDestination != null && trimmedSource != null) {
                    plugin.getLogger().severe("Error executing transaction from " + trimmedSource + " to " + trimmedDestination + ": " + e.getMessage());
                } else if (trimmedSource != null) {
                    plugin.getLogger().severe("Error executing transaction from " + trimmedSource + ": " + e.getMessage());
                } else if (trimmedDestination != null) {
                    plugin.getLogger().severe("Error executing transaction to " + trimmedDestination + ": " + e.getMessage());
                } else {
                    plugin.getLogger().severe("Error executing transaction: " + e.getMessage());
                }
                throw e;
            } finally {
                try {
                    if (!conn.getAutoCommit()) {
                        conn.commit();
                    }
                } catch (SQLException e) {
                    plugin.getLogger().severe("Error committing transaction: " + e.getMessage());
                    throw e;
                }
            }
        }));
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
            sql = "SELECT TransactionDatetime, Amount, SourceUUID, DestinationUUID, SourcePlayerName, DestinationPlayerName, Message FROM " + viewName + " ORDER BY TransactionDatetime DESC LIMIT ? OFFSET ?";
        } else if (displayPassed) {
            // Display only passed transactions
            sql = "SELECT TransactionDatetime, Amount, SourceUUID, DestinationUUID, SourcePlayerName, DestinationPlayerName, Message FROM " + viewName + " WHERE Passed = 'Passed' ORDER BY TransactionDatetime DESC LIMIT ? OFFSET ?";
        } else {
            // Display only failed transactions
            sql = "SELECT TransactionDatetime, Amount, SourceUUID, DestinationUUID, SourcePlayerName, DestinationPlayerName, Message FROM " + viewName + " WHERE Passed = 'Failed' ORDER BY TransactionDatetime DESC LIMIT ? OFFSET ?";
        }

        return Utility.executeQueryAsync(conn -> {
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
}
