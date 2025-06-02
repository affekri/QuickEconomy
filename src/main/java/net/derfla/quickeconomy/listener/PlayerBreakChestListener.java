package net.derfla.quickeconomy.listener;

import net.derfla.quickeconomy.Main;
import net.derfla.quickeconomy.database.Shop;
import net.derfla.quickeconomy.util.BlockOwner;
import net.derfla.quickeconomy.util.FindChest;
import net.derfla.quickeconomy.util.Styles;
import net.derfla.quickeconomy.util.TypeChecker;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.plugin.Plugin;

public class PlayerBreakChestListener implements Listener {

    static Plugin plugin = Main.getInstance();

    @EventHandler
    public void onPlayerBreakChest (BlockBreakEvent event) {
        if (!(event.getBlock().getState() instanceof Chest)) return;
        if (!event.getBlock().getType().equals(Material.CHEST)) return;

        Chest chest = (Chest) event.getBlock().getState();
        Player player = event.getPlayer();
        String playerUUID = TypeChecker.trimUUID(player.getUniqueId().toString());

        boolean isShopChest = BlockOwner.isLocked(chest); 

        if (BlockOwner.isLockedForPlayer(chest, playerUUID)) {
            // Chest is locked for this player. They can only break it if it's a shop and they have destroyall perm.
            if (isShopChest && player.hasPermission("quickeconomy.shop.destroyall")) {
                // Admin with destroyall can break this shop chest. Proceed with shop cleanup.
            } else {
                // Not a shop admin or not a shop chest they own/can break.
                player.sendMessage(Component.translatable("shop.breakchest.canceled", Styles.ERRORSTYLE));
                event.setCancelled(true);
                return;
            }
        }

        // If we reach here, player is allowed to break the chest block itself.
        // Now, if it was a shop chest, perform shop-specific cleanup.
        if (isShopChest) {
            if (Main.SQLMode) {
                Location chestLoc = chest.getLocation();
                Shop.removeShop(chestLoc.getBlockX(), chestLoc.getBlockY(), chestLoc.getBlockZ())
                    .exceptionally(ex -> {
                        plugin.getLogger().warning("Failed to remove shop from database (chest break): " + ex.getMessage());
                        return null;
                    });
            }

            // Attempt to find and break the associated shop sign.
            // Signs are typically attached to a wall block adjacent to the chest or directly on the chest (less common).
            BlockFace[] adjacentFaces = { BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST }; 
            Sign shopSignToRemove = null;

            for (BlockFace face : adjacentFaces) {
                Block potentialSignBlock = chest.getBlock().getRelative(face);
                if (potentialSignBlock.getState() instanceof Sign) {
                    Sign foundSign = (Sign) potentialSignBlock.getState();
                    // Verify this sign is linked to THIS chest and is a [SHOP] sign.
                    if (FindChest.get(foundSign) == chest) { // Check if sign points to this chest
                        Component shopHeaderStyled = PlayerPlaceSignListener.getShopHeaderComponent();
                        if (!foundSign.getSide(Side.FRONT).lines().isEmpty() && 
                            TypeChecker.getRawString(foundSign.getSide(Side.FRONT).lines().get(0)).equalsIgnoreCase("[SHOP]") &&
                            foundSign.getSide(Side.FRONT).lines().get(0).style().equals(shopHeaderStyled.style())) {
                            shopSignToRemove = foundSign;
                            break; 
                        }
                    }
                }
            }

            if (shopSignToRemove != null) {
                shopSignToRemove.getBlock().breakNaturally();
                player.sendMessage(Component.translatable("shop.associated_sign_removed", Styles.INFOSTYLE));
            }
            player.sendMessage(Component.translatable("shop.chest.destroyed", Styles.INFOSTYLE));
        }
    }
}
