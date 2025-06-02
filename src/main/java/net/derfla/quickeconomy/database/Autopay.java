package net.derfla.quickeconomy.database;

import net.derfla.quickeconomy.Main;
import net.derfla.quickeconomy.util.TypeChecker;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class Autopay {

    static Plugin plugin = Main.getInstance();

    public static CompletableFuture<Void> addAutopay(String autopayName, @NotNull String uuid, @NotNull String destination,
                                                     double amount, int inverseFrequency, int timesLeft) {
        String trimmedUuid = TypeChecker.trimUUID(uuid);
        String trimmedDestination = TypeChecker.trimUUID(destination);

        if (amount < 0) {
            plugin.getLogger().severe("Error: Amount must be greater than 0.");
            return CompletableFuture.failedFuture(new IllegalArgumentException("Amount must be greater than 0."));
        }
        if (inverseFrequency < 0) {
            plugin.getLogger().severe("Error: InverseFrequency must be greater than 0.");
            return CompletableFuture.failedFuture(new IllegalArgumentException("InverseFrequency must be greater than 0."));
        }
        if (timesLeft < 0) { // timesLeft can be 0 for indefinite, or > 0 for specific count
            plugin.getLogger().severe("Error: TimesLeft must be 0 (for continuous) or greater.");
            return CompletableFuture.failedFuture(new IllegalArgumentException("TimesLeft must be 0 or greater."));
        }

        Instant currentTime = Instant.now(); // Use Instant for UTC time
        // AutopayDatetime is DATETIME SQL type, format "yyyy-MM-dd HH:mm:ss" in UTC
        String autopayDateTimeFormatted = currentTime.atZone(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        String sql = "INSERT INTO Autopays (" +
                "    Active, AutopayDatetime, AutopayName, Source, Destination," +
                "    Amount, InverseFrequency, TimesLeft" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?);";

        return Utility.executeUpdateAsync(conn -> {
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, 1); // Active
                pstmt.setString(2, autopayDateTimeFormatted); // AutopayDatetime formatted for SQL DATETIME (UTC)
                pstmt.setString(3, autopayName); // AutopayName
                pstmt.setString(4, trimmedUuid); // Source
                pstmt.setString(5, trimmedDestination); // Destination
                pstmt.setDouble(6, amount); // Amount
                pstmt.setInt(7, inverseFrequency); // InverseFrequency
                pstmt.setInt(8, timesLeft); // TimesLeft

                pstmt.executeUpdate();
                plugin.getLogger().info("Autopay added successfully");
            }
        }).exceptionally(ex -> {
            plugin.getLogger().severe("Error adding autopay for Source: " + trimmedUuid + ", Name: " + autopayName + " - " + ex.getMessage());
            if (ex instanceof CompletionException) throw (CompletionException) ex;
            throw new CompletionException(ex);
        });
    }

    public static CompletableFuture<Void> stateChangeAutopay(boolean activeState, int autopayID, @NotNull String uuid) {
        String trimmedUuid = TypeChecker.trimUUID(uuid);
        String sql = "UPDATE Autopays SET Active = ? WHERE AutopayID = ? AND Source = ?;";

        return Utility.executeUpdateAsync(conn -> {
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setBoolean(1, activeState);
                pstmt.setInt(2, autopayID);
                pstmt.setString(3, trimmedUuid);

                int rowsAffected = pstmt.executeUpdate();
                if (rowsAffected > 0) {
                    plugin.getLogger().info("Autopay updated successfully");
                } else {
                    plugin.getLogger().info("Autopay not found. No update was performed.");
                }
            } catch (SQLException e) {
                String action = activeState ? "activating" : "deactivating";
                plugin.getLogger().severe("Error " + action + " autopay: " + e.getMessage());
                throw e; // Re-throw to let executeUpdateAsync handle it
            }
        }).exceptionally(ex -> {
            plugin.getLogger().severe("Outer error toggling autopay #" + autopayID + " - " + ex.getMessage());
            if (ex instanceof CompletionException) throw (CompletionException) ex;
            throw new CompletionException(ex);
        });
    }

    public static CompletableFuture<Void> deleteAutopay(int autopayID, @NotNull String uuid) {
        String trimmedUuid = TypeChecker.trimUUID(uuid);
        String sql = "DELETE FROM Autopays WHERE AutopayID = ? AND Source = ?;";

        return Utility.executeUpdateAsync(conn -> {
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, autopayID);
                pstmt.setString(2, trimmedUuid);

                int rowsAffected = pstmt.executeUpdate();
                if (rowsAffected > 0) {
                    plugin.getLogger().info("Autopay #" + autopayID + " deleted successfully");
                } else {
                    plugin.getLogger().info("Autopay #" + autopayID + " not found. No deletion was performed.");
                }
            }
        }).exceptionally(ex -> {
            plugin.getLogger().severe("Error deleting autopay #" + autopayID + ", Source: " + trimmedUuid + " - " + ex.getMessage());
            if (ex instanceof CompletionException) throw (CompletionException) ex;
            throw new CompletionException(ex);
        });
    }

    public static CompletableFuture<List<Map<String, Object>>> viewAutopays(@NotNull String uuid) {
        String trimmedUuid = TypeChecker.trimUUID(uuid);
        String sql = "SELECT a.AutopayID, a.AutopayName, a.Amount, pa.PlayerName AS DestinationName, a.InverseFrequency, a.TimesLeft " +
                "FROM Autopays a " +
                "JOIN PlayerAccounts pa ON a.Destination = pa.UUID " +
                "WHERE a.Source = ? " +
                "ORDER BY a.AutopayDatetime DESC";

        return Utility.executeQueryAsync(conn -> {
            List<Map<String, Object>> autopays = new ArrayList<>();
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, trimmedUuid);

                try (ResultSet rs = pstmt.executeQuery()) {
                    ResultSetMetaData metaData = rs.getMetaData();
                    int columnCount = metaData.getColumnCount();

                    while (rs.next()) {
                        Map<String, Object> autopay = new HashMap<>();
                        for (int i = 1; i <= columnCount; i++) {
                            String columnName = metaData.getColumnName(i);
                            Object value = rs.getObject(i);
                            autopay.put(columnName, value);
                        }
                        autopays.add(autopay);
                    }
                }
            }
            return autopays; // Return the list of autopays
        });
    }
    
}
