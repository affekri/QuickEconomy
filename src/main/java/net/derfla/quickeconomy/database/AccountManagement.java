package net.derfla.quickeconomy.database;

import net.derfla.quickeconomy.Main;
import net.derfla.quickeconomy.model.PlayerAccount;
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
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;


/**
 * Database access layer for player account records.
 * <p>
 * Provides asynchronous CRUD-style operations for the {@code PlayerAccounts} table
 * and related convenience queries used throughout the plugin. Methods return
 * {@link java.util.concurrent.CompletableFuture} to avoid blocking the main server thread.
 */
public class AccountManagement {

    static Plugin plugin = Main.getInstance();

    /**
     * Create a new player account if one does not already exist, and create the
     * per-player transactions view afterwards.
     *
     * @param uuid        the player's UUID, with or without dashes
     * @param playerName  the player's current name
     * @param balance     initial balance to set
     * @param change      initial balance change snapshot
     * @param callback    optional callback that will be invoked after the insert completes
     * @return a future completing when the account is created and view ensured, or immediately if the account already exists
     */
    public static CompletableFuture<Void> addAccount(@NotNull String uuid, @NotNull String playerName, double balance, double change, Consumer<Void> callback) {
        String trimmedUuid = TypeChecker.trimUUID(uuid);
        Instant currentTime = Instant.now();
        String currentTimeString = TypeChecker.convertToUTC(currentTime.atZone(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        return accountExists(trimmedUuid).thenCompose(exists -> {
                    if (exists) {
                        plugin.getLogger().info("Account already exists for player with UUID: " + trimmedUuid);
                        return CompletableFuture.completedFuture(null);
                    } else {
                        String insertSql = "INSERT INTO PlayerAccounts (UUID, AccountDatetime, PlayerName, Balance, BalChange) "
                                + "VALUES (?, ?, ?, ?, ?)";
                        return Utility.executeUpdateAsync(conn -> {
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
                            }
                        });
                    }
                }).thenCompose(v -> TableManagement.createTransactionsView(trimmedUuid))
                .exceptionally(ex -> {
                    plugin.getLogger().severe("Error during addAccount operation for UUID: " + trimmedUuid + " PlayerName: " + playerName + " - " + ex.getMessage());
                    if (ex instanceof CompletionException) throw (CompletionException) ex;
                    throw new CompletionException(ex);
                });
    }

    /**
     * Check whether an account exists for the given UUID.
     *
     * @param uuid the player's UUID, with or without dashes
     * @return a future that completes with {@code true} if an account exists; {@code false} otherwise
     */
    public static CompletableFuture<Boolean> accountExists(@NotNull String uuid) {
        String sql = "SELECT COUNT(*) FROM PlayerAccounts WHERE UUID = ?";
        String trimmedUuid = TypeChecker.trimUUID(uuid);

        return Utility.executeQueryAsync(conn -> {
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, trimmedUuid);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1) > 0; // Return true if count is greater than 0
                    }
                }
            }
            return false;
        });
    }

    /**
     * Update the stored player name for the given UUID if it has changed.
     *
     * @param uuid           the player's UUID, with or without dashes
     * @param newPlayerName  the new player name to store
     * @return a future that completes when the update has been applied or no-op'ed if unchanged/nonexistent
     */
    public static CompletableFuture<Void> updatePlayerName(String uuid, String newPlayerName) {
        String trimmedUuid = TypeChecker.trimUUID(uuid);
        String untrimmedUuid = TypeChecker.untrimUUID(uuid);

        // First get the current player name to check if it's different
        String getCurrentNameSql = "SELECT PlayerName FROM PlayerAccounts WHERE UUID = ?";

        return Utility.executeQueryAsync(conn -> {
            try (PreparedStatement pstmt = conn.prepareStatement(getCurrentNameSql)) {
                pstmt.setString(1, trimmedUuid);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("PlayerName");
                    }
                }
            }
            return null; // Return null if not found
        }).thenCompose(currentName -> {
            if (currentName == null) {
                plugin.getLogger().info("No account found for UUID: " + untrimmedUuid);
                return CompletableFuture.completedFuture(null);
            }

            // Only update if the name has actually changed
            if (currentName.equals(newPlayerName)) {
                // Name hasn't changed, no need to update or log
                return CompletableFuture.completedFuture(null);
            }

            // Name has changed, proceed with update
            String updateSql = "UPDATE PlayerAccounts SET PlayerName = ? WHERE UUID = ?";
            return Utility.executeUpdateAsync(conn -> {
                try (PreparedStatement pstmt = conn.prepareStatement(updateSql)) {
                    pstmt.setString(1, newPlayerName);
                    pstmt.setString(2, trimmedUuid);
                    int rowsAffected = pstmt.executeUpdate();

                    if (rowsAffected > 0) {
                        plugin.getLogger().info("Updated player name for UUID " + untrimmedUuid + ": " + currentName + " -> " + newPlayerName);
                    }
                }
            });
        }).exceptionally(ex -> {
            plugin.getLogger().severe("Error updating player name for UUID: " + untrimmedUuid + " - " + ex.getMessage());
            if (ex instanceof CompletionException) throw (CompletionException) ex;
            throw new CompletionException(ex);
        });
    }

    /**
     * Retrieve the current balance for the given UUID.
     *
     * @param uuid the player's UUID, with or without dashes
     * @return a future that completes with the current balance, or 0.0 when not found
     */
    public static CompletableFuture<Double> displayBalance(@NotNull String uuid) {
        String trimmedUuid = TypeChecker.trimUUID(uuid);
        String sql = "SELECT Balance FROM PlayerAccounts WHERE UUID = ?";

        return Utility.executeQueryAsync(conn -> {
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, trimmedUuid);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getDouble("Balance"); // Return the balance
                    }
                }
            }
            return 0.0; // Return 0 if no balance found or an error occurred
        });
    }

    // Synchronous method for rollback purposes
    /**
     * Synchronous helper used by transactional logic to fetch a player's balance within an existing connection.
     *
     * @param conn active SQL connection participating in a broader transaction
     * @param uuid the player's UUID, with or without dashes
     * @return the current balance, or 0.0 when not found
     * @throws SQLException if an SQL error occurs
     */
    static double displayBalanceSync(Connection conn, @NotNull String uuid) throws SQLException {
        String trimmedUuid = TypeChecker.trimUUID(uuid);
        String sql = "SELECT Balance FROM PlayerAccounts WHERE UUID = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, trimmedUuid);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble("Balance");
                }
            }
        }
        return 0.0; // Return 0 if no balance found
    }

    /**
     * Set a player's balance and balance change snapshot.
     *
     * @param uuid    the player's UUID, with or without dashes
     * @param balance the new balance to set
     * @param change  the new balance change value to set
     * @return a future that completes when the update has been applied
     */
    public static CompletableFuture<Void> setPlayerBalance(@NotNull String uuid, double balance, double change) {
        String trimmedUuid = TypeChecker.trimUUID(uuid);
        String untrimmedUuid = TypeChecker.untrimUUID(uuid);
        String sql = "UPDATE PlayerAccounts SET Balance = ?, BalChange = ? WHERE UUID = ?;";

        return Utility.executeUpdateAsync(conn -> {
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
            }
        }).exceptionally(ex -> {
            plugin.getLogger().severe("Error setting player balance for UUID: " + untrimmedUuid + " - " + ex.getMessage());
            if (ex instanceof CompletionException) throw (CompletionException) ex;
            throw new CompletionException(ex);
        });
    }

    /**
     * Get the last recorded balance change value for an account.
     *
     * @param uuid the player's UUID, with or without dashes
     * @return a future that completes with the change value, or 0.0 when absent
     */
    public static CompletableFuture<Double> getPlayerBalanceChange(String uuid) {
        String trimmedUUID = TypeChecker.trimUUID(uuid);
        String sql = "SELECT BalChange FROM PlayerAccounts WHERE UUID = ?";

        return Utility.executeQueryAsync(conn -> {
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, trimmedUUID);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getDouble("BalChange");
                    }
                }
            }
            return 0.0; // Return 0.0 if no change found
        });
    }

    /**
     * Update the balance change value for an account.
     *
     * @param uuid   the player's UUID, with or without dashes
     * @param change the new change value to record
     * @return a future that completes when the update has been applied
     */
    public static CompletableFuture<Void> setPlayerBalanceChange(@NotNull String uuid, double change) {
        String trimmedUuid = TypeChecker.trimUUID(uuid);
        String sql = "UPDATE PlayerAccounts SET BalChange = ? WHERE UUID = ?;";

        return Utility.executeUpdateAsync(conn -> {
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setDouble(1, change);
                pstmt.setString(2, trimmedUuid);
                pstmt.executeUpdate();
            }
        }).exceptionally(ex -> {
            plugin.getLogger().severe("Error setting player balance change for UUID: " + uuid + " - " + ex.getMessage());
            if (ex instanceof CompletionException) throw (CompletionException) ex;
            throw new CompletionException(ex);
        });
    }


    /**
     * Retrieve all accounts from the database.
     *
     * @return a future that completes with a map keyed by trimmed UUID, where each value is a {@link PlayerAccount}
     */
    public static CompletableFuture<HashMap<String, PlayerAccount>> listAllAccounts() {
        String sql = "SELECT UUID, PlayerName, Balance, BalChange, AccountDatetime AS Created FROM PlayerAccounts";

        return Utility.executeQueryAsync(conn -> {
            HashMap<String, PlayerAccount> accountMap = new HashMap<>();
            try (PreparedStatement pstmt = conn.prepareStatement(sql);
                 ResultSet rs = pstmt.executeQuery()) {

                while (rs.next()) {
                    String uuid = rs.getString("UUID");
                    String playerName = rs.getString("PlayerName");
                    double balance = rs.getDouble("Balance");
                    double change = rs.getDouble("BalChange");
                    String accountDatetimeUTC = rs.getString("Created");
                    String accountDatetimeLocal = TypeChecker.convertToLocalTime(accountDatetimeUTC);

                    PlayerAccount account = new PlayerAccount(playerName, balance, change, accountDatetimeLocal);
                    accountMap.put(uuid, account);
                }
            }
            return accountMap;
        });
    }

    /**
     * Look up a UUID by the stored player name.
     *
     * @param playerName the player name to search for
     * @return a future that completes with the UUID string when found, or {@code null} if not found
     */
    public static CompletableFuture<String> getUUID(String playerName) {
        String sql = "SELECT UUID FROM PlayerAccounts WHERE PlayerName = ?";

        return Utility.executeQueryAsync(conn -> {
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, playerName);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("UUID");
                    }
                }
            }
            return null; // Return null if not found
        });
    }
}
