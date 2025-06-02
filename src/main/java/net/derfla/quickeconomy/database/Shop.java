package net.derfla.quickeconomy.database;

import net.derfla.quickeconomy.Main;
import net.derfla.quickeconomy.util.TypeChecker;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class Shop {

    static Plugin plugin = Main.getInstance();

    public static CompletableFuture<Void> addShop(@NotNull int x_coord, @NotNull int y_coord, @NotNull int z_coord, @NotNull String owner1, String owner2) {
        String Owner1 = TypeChecker.trimUUID(owner1);
        final String Owner2 = (owner2 == null || owner2.isEmpty()) ? null : TypeChecker.trimUUID(owner2);

        return shopExists(x_coord, y_coord, z_coord).thenCompose(exists -> {
            if (exists) {
                return CompletableFuture.completedFuture(null);
            }

            // Get current UTC time for CreationDatetime
            Instant currentTime = Instant.now();
            String currentTimeString = TypeChecker.convertToUTC(currentTime.atZone(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

            String sql = "INSERT INTO Shops (CreationDatetime, x_coord, y_coord, z_coord, Owner1, Owner2) VALUES (?, ?, ?, ?, ?, ?)";
            return Utility.executeUpdateAsync(conn -> {
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, currentTimeString);
                    pstmt.setInt(2, x_coord);
                    pstmt.setInt(3, y_coord);
                    pstmt.setInt(4, z_coord);
                    pstmt.setString(5, Owner1);
                    pstmt.setString(6, Owner2);
                    pstmt.executeUpdate();
                }
            });
        });
    }

    public static CompletableFuture<Void> removeShop(int x_coord, int y_coord, int z_coord) {
        String sql = "DELETE FROM Shops WHERE x_coord = ? AND y_coord = ? AND z_coord = ?;";

        return Utility.executeUpdateAsync(conn -> {
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, x_coord);
                pstmt.setInt(2, y_coord);
                pstmt.setInt(3, z_coord);
                int rowsAffected = pstmt.executeUpdate();
                if (rowsAffected > 0) {
                    plugin.getLogger().info("Shop at " + x_coord + ", " + y_coord + ", " + z_coord + " removed from the database.");
                } else {
                    // only show for debugging reasons
                    //plugin.getLogger().info("No shop found with coordinates " + x_coord + ", " + y_coord + ", " + z_coord + " to remove.");
                }
            }
        }).exceptionally(ex -> {
            plugin.getLogger().severe("Error removing empty shop for coords: " + x_coord + ", " + y_coord + ", " + z_coord + " - " + ex.getMessage());
            if (ex instanceof CompletionException) throw (CompletionException) ex;
            throw new CompletionException(ex);
        });
    }

    public static CompletableFuture<Void> setShopEmpty(int x_coord, int y_coord, int z_coord) {
        String sql = "UPDATE Shops SET IsEmpty = NOW() WHERE x_coord = ? AND y_coord = ? AND z_coord = ?";
        
        return Utility.executeUpdateAsync(conn -> {
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, x_coord);
                pstmt.setInt(2, y_coord);
                pstmt.setInt(3, z_coord);
                pstmt.executeUpdate();
            }
        });
    }

    public static CompletableFuture<Void> unsetShopEmpty(int x_coord, int y_coord, int z_coord) {
        String sql = "UPDATE Shops SET IsEmpty = NULL WHERE x_coord = ? AND y_coord = ? AND z_coord = ?";
        return Utility.executeUpdateAsync(conn -> {
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, x_coord);
                pstmt.setInt(2, y_coord);
                pstmt.setInt(3, z_coord);
                pstmt.executeUpdate();
            }
        });
    }

    private static CompletableFuture<Boolean> shopExists(@NotNull int x_coord, @NotNull int y_coord, @NotNull int z_coord) {
        String sql = "SELECT COUNT(*) FROM Shops WHERE x_coord = ? AND y_coord = ? AND z_coord = ?";

        return Utility.executeQueryAsync(conn -> {
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, x_coord);
                pstmt.setInt(2, y_coord);
                pstmt.setInt(3, z_coord);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1) > 0; // Return true if the shop exists
                    }
                }
            }
            return false; // Return false if the shop does not exist or an error occurred
        });
    }

    public static CompletableFuture<List<String>> displayShopsView(@NotNull String uuid) {
        String trimmedUuid = TypeChecker.trimUUID(uuid);
        String viewName = "vw_Shops_" + trimmedUuid;
        String checkViewSQL = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.VIEWS WHERE TABLE_NAME = ? AND TABLE_SCHEMA = ?";
        String databaseName = plugin.getConfig().getString("database.database");

        return Utility.executeQueryAsync(conn -> {
            List<String> shops = new ArrayList<>();

            // Check if the view for that player exists
            try (PreparedStatement pstmt = conn.prepareStatement(checkViewSQL)) {
                pstmt.setString(1, viewName);
                pstmt.setString(2, databaseName);

                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next() && rs.getInt(1) == 0) {
                        return shops; // Return empty list if the view does not exist
                    }
                }
            }

            // If the view exists, proceed to retrieve the coordinates
            String sql = "SELECT x_coord, y_coord, z_coord, CASE WHEN IsEmpty IS NULL THEN 0 ELSE 1 END AS IsEmpty FROM " + viewName + ";";
            try (PreparedStatement pstmt = conn.prepareStatement(sql);
                 ResultSet rs = pstmt.executeQuery()) {
                // retrieve x, y, z coordinates and IsEmpty (1 if not null, 0 if null)
                while (rs.next()) {
                    int x_coord = rs.getInt("x_coord");
                    int y_coord = rs.getInt("y_coord");
                    int z_coord = rs.getInt("z_coord");
                    int isEmpty = rs.getInt("IsEmpty");
                    
                    String coordinates = x_coord + "," + y_coord + "," + z_coord;
                    shops.add(coordinates + " (" + isEmpty + ")");
                }
            }

            return shops; // Return the list of shops
        });
    }
}
