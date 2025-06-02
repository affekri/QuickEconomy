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


public class AccountManagement {

    static Plugin plugin = Main.getInstance();

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
                .thenCompose(v -> TableManagement.createShopsView(trimmedUuid))
                .exceptionally(ex -> {
                    plugin.getLogger().severe("Error during addAccount operation for UUID: " + trimmedUuid + " PlayerName: " + playerName + " - " + ex.getMessage());
                    if (ex instanceof CompletionException) throw (CompletionException) ex;
                    throw new CompletionException(ex);
                });
    }

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

    public static CompletableFuture<Double> getPlayerBalance(@NotNull String uuid) {
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
