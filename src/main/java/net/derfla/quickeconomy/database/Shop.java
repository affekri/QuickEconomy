package net.derfla.quickeconomy.database;

import net.derfla.quickeconomy.Main;
import net.derfla.quickeconomy.util.TypeChecker;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static net.derfla.quickeconomy.database.TableManagement.createEmptyShopsView;

public class Shop {

    static Plugin plugin = Main.getInstance();

    public static CompletableFuture<Boolean> insertEmptyShop(@NotNull String coordinates, @NotNull String owner1, String owner2) {
        String Owner1 = TypeChecker.trimUUID(owner1);
        final String Owner2 = !owner2.isEmpty() ? TypeChecker.trimUUID(owner2) : "";

        return emptyShopExists(coordinates).thenCompose(exists -> {
            if (exists) {
                // Update the owners of the existing shop
                String updateSql = "UPDATE EmptyShops SET Owner1 = ?, Owner2 = ? WHERE Coordinates = ?";
                return Utility.executeUpdateAsync(conn -> {
                    try (PreparedStatement pstmt = conn.prepareStatement(updateSql)) {
                        pstmt.setString(1, Owner1);
                        pstmt.setString(2, Owner2);
                        pstmt.setString(3, coordinates);
                        pstmt.executeUpdate();
                        plugin.getLogger().info("Empty shop at " + coordinates + " updated with new owners.");
                    }
                }).thenCompose(v -> { // Use thenCompose for sequential async operations
                    CompletableFuture<Void> future1 = createEmptyShopsView(owner1);
                    if (Owner2 != null && !Owner2.isEmpty()) {
                        return future1.thenCompose(v2 -> createEmptyShopsView(Owner2));
                    }
                    return future1;
                }).thenApply(v -> true); // Return true if an existing shop was updated
            } else {
                // Insert a new empty shop
                String insertSql = "INSERT INTO EmptyShops (Coordinates, Owner1, Owner2) VALUES (?, ?, ?)";
                return Utility.executeUpdateAsync(conn -> {
                    try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
                        pstmt.setString(1, coordinates);
                        pstmt.setString(2, Owner1);
                        pstmt.setString(3, Owner2);
                        pstmt.executeUpdate();
                        plugin.getLogger().info("Empty shop registered at " + coordinates);
                    }
                }).thenCompose(v -> { // Use thenCompose for sequential async operations
                    CompletableFuture<Void> future1 = createEmptyShopsView(owner1);
                    if (Owner2 != null && !Owner2.isEmpty()) {
                        return future1.thenCompose(v2 -> createEmptyShopsView(Owner2));
                    }
                    return future1;
                }).thenApply(v -> false); // Return false if a new shop was created
            }
        }).exceptionally(ex -> {
            plugin.getLogger().severe("Error registering empty shop for coords: " + coordinates + " - " + ex.getMessage());
            if (ex instanceof CompletionException) throw (CompletionException) ex;
            throw new CompletionException(ex);
        });
    }

    private static CompletableFuture<Boolean> emptyShopExists(@NotNull String coordinates) {
        String sql = "SELECT COUNT(*) FROM EmptyShops WHERE Coordinates = ?";

        return Utility.executeQueryAsync(conn -> {
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, coordinates);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1) > 0; // Return true if the shop exists
                    }
                }
            }
            return false; // Return false if the shop does not exist or an error occurred
        });
    }


    public static CompletableFuture<List<String>> displayEmptyShopsView(@NotNull String uuid) {
        String trimmedUuid = TypeChecker.trimUUID(uuid);
        String viewName = "vw_EmptyShops_" + trimmedUuid;
        String checkViewSQL = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.VIEWS WHERE TABLE_NAME = ? AND TABLE_SCHEMA = ?";
        String databaseName = plugin.getConfig().getString("database.database");

        return Utility.executeQueryAsync(conn -> {
            List<String> emptyShops = new ArrayList<>();

            // Check if the view for that player exists
            try (PreparedStatement pstmt = conn.prepareStatement(checkViewSQL)) {
                pstmt.setString(1, viewName);
                pstmt.setString(2, databaseName);

                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next() && rs.getInt(1) == 0) {
                        return emptyShops; // Return empty list if the view does not exist
                    }
                }
            }


            // If the view exists, proceed to retrieve the coordinates
            String sql = "SELECT Coordinates FROM " + viewName + ";";
            try (PreparedStatement pstmt = conn.prepareStatement(sql);
                 ResultSet rs = pstmt.executeQuery()) {

                while (rs.next()) {
                    String coordinates = rs.getString("Coordinates");
                    emptyShops.add(coordinates);
                }
            }

            return emptyShops; // Return the list of empty shops
        });
    }

    public static CompletableFuture<Void> removeEmptyShop(String coordinates) {
        String sql = "DELETE FROM EmptyShops WHERE Coordinates = ?;";

        return Utility.executeUpdateAsync(conn -> {
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, coordinates);
                int rowsAffected = pstmt.executeUpdate();
                if (rowsAffected > 0) {
                    plugin.getLogger().info("Empty shop at " + coordinates + " removed from the database.");
                } else {
                    // only show for debugging reasons
                    //plugin.getLogger().info("No empty shop found with coordinates " + coordinates + " to remove.");
                }
            }
        }).exceptionally(ex -> {
            plugin.getLogger().severe("Error removing empty shop for coords: " + coordinates + " - " + ex.getMessage());
            if (ex instanceof CompletionException) throw (CompletionException) ex;
            throw new CompletionException(ex);
        });
    }
}
