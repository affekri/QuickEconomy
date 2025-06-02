package net.derfla.quickeconomy.listener;

import net.derfla.quickeconomy.Main;
import net.derfla.quickeconomy.database.Shop;
import net.derfla.quickeconomy.util.BlockOwner;
import net.derfla.quickeconomy.util.ShopInventory;
import net.derfla.quickeconomy.util.TypeChecker;
import org.bukkit.Location;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.plugin.Plugin;

import java.util.List;

public class PlayerCloseInventoryListener implements Listener {

    private static final Plugin plugin = Main.getInstance();

    @EventHandler
    public void onPlayerCloseInventory (InventoryCloseEvent event) {
        if (!event.getInventory().getType().equals(InventoryType.CHEST)) return;

        Chest chest = null;

        if (event.getInventory().getHolder() instanceof ShopInventory) {
            // This is the ShopInventory GUI (player buying from shop)
            ShopInventory shopInventory = (ShopInventory) event.getInventory().getHolder();
            Chest shopGuiChest = shopInventory.getShopChest(); 
            if (shopGuiChest != null) {
                BlockOwner.setShopOpen(shopGuiChest, false);
                // For ShopInventory GUI, inventory changes are handled by InventoryClickListener
            }
            return;
        }
        
        // Direct chest interaction (owner stocking, or anyone opening a non-shop chest)
        try {
            if (event.getInventory().getLocation() != null && event.getInventory().getLocation().getBlock().getState() instanceof Chest) {
                chest = (Chest) event.getInventory().getLocation().getBlock().getState();
            } else {
                return;
            }
        } catch (NullPointerException e) {
            plugin.getLogger().fine("NullPointerException accessing chest from inventory close event");
            return; 
        }

        if (chest == null) return;

        Player player = (Player) event.getPlayer();
        List<String> owners = BlockOwner.getChestOwner(chest);

        // Check if this chest is a shop AND the player closing is an owner
        if (owners != null && !owners.isEmpty() && owners.contains(TypeChecker.trimUUID(player.getUniqueId().toString()))) {
            BlockOwner.setShopOpen(chest, false);

            if (Main.SQLMode) {
                Location chestLoc = chest.getLocation();
                final Chest finalChest = chest;

                if (finalChest.getBlockInventory().isEmpty()) {
                    Shop.setShopEmpty(chestLoc.getBlockX(), chestLoc.getBlockY(), chestLoc.getBlockZ())
                        .exceptionally(ex -> {
                            plugin.getLogger().warning("Failed to set shop as empty on inventory close by owner: " + ex.getMessage());
                            return null;
                        });
                } else {
                    Shop.unsetShopEmpty(chestLoc.getBlockX(), chestLoc.getBlockY(), chestLoc.getBlockZ())
                        .exceptionally(ex -> {
                            plugin.getLogger().warning("Failed to unset shop as empty on inventory close by owner: " + ex.getMessage());
                            return null;
                        });
                }
            }
        } else {
            // If it's a shop chest but not closed by an owner, or not a shop chest at all,
            // still ensure the shopOpen flag is cleared if it was somehow set.
            // This primarily handles the case where a shop GUI was used and closed,
            // or if a chest that *was* a shop is no longer one but still has the PDC tag.
            if (BlockOwner.isShop(chest)) {
                BlockOwner.setShopOpen(chest, false);
            }
        }
    }
}
