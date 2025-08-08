package net.derfla.quickeconomy.listener;

import net.derfla.quickeconomy.util.BlockOwner;
import net.derfla.quickeconomy.util.ShopInventory;
import org.bukkit.block.Chest;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;

/**
 * Listener class that handles inventory close events.
 * Manages the state of shop inventories and synchronizes changes when players close them.
 */
public class PlayerCloseInventoryListener implements Listener {

    /**
     * Handles the event when a player closes an inventory.
     * Manages shop inventory state transitions, updates chest contents,
     * and ensures proper synchronization between shop inventories and their associated chests.
     *
     * @param event The InventoryCloseEvent containing information about the closed inventory
     */
    @EventHandler
    public void onPlayerCloseInventory (InventoryCloseEvent event) {
        if (!event.getInventory().getType().equals(InventoryType.CHEST)) return;
        Chest chest;
        if (event.getInventory().getHolder() instanceof ShopInventory) {
            chest = ShopInventory.getShopChest();
            BlockOwner.setShopOpen(chest, false);
            chest.getBlockInventory().setContents(event.getInventory().getContents());
            return;
        }
        try {
            event.getInventory().getLocation().getBlock();
        } catch (NullPointerException e) {
            return;
        }
        if (event.getInventory().getLocation().getBlock().getState() instanceof Chest){
            chest = (Chest) event.getInventory().getLocation().getBlock().getState();
        }  else return;
        if (chest == null) return;

        if (BlockOwner.isShop(chest)) {
            BlockOwner.setShopOpen(chest, false);
            return;
        }
    }
}
