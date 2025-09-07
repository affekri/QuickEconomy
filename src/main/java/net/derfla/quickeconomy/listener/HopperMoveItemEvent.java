package net.derfla.quickeconomy.listener;

import net.derfla.quickeconomy.util.BlockOwner;
import org.bukkit.block.Chest;
import org.bukkit.entity.minecart.HopperMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;

/**
 * Listener class that handles hopper minecart inventory movement events.
 * Prevents hopper minecarts from extracting items from locked shop chests.
 */
public class HopperMoveItemEvent implements Listener {

    /**
     * Handles the event when a hopper minecart attempts to move items from an inventory.
     * Prevents hopper minecarts from extracting items from locked shop chests,
     * maintaining shop security and preventing unauthorized item theft.
     *
     * @param event The InventoryMoveItemEvent containing information about the item movement attempt
     */
    @EventHandler
    public void onHopperMoveItem(InventoryMoveItemEvent event) {
        if (!(event.getInitiator().getHolder() instanceof HopperMinecart)) return;
        if (!event.getInitiator().getType().equals(InventoryType.HOPPER)) return;
        if (!event.getSource().getType().equals(InventoryType.CHEST)) return;
        if (event.getSource().getLocation().getBlock() == null) return;
        if (!(event.getSource().getLocation().getBlock().getState() instanceof Chest)) return;
        Chest chest = (Chest) event.getSource().getLocation().getBlock().getState();
        if (!BlockOwner.isLocked(chest)) return;
        event.setCancelled(true);
        HopperMinecart minecart = (HopperMinecart) event.getInitiator().getHolder();
        minecart.setEnabled(false);
    }
}
