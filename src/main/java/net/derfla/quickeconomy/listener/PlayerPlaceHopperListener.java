package net.derfla.quickeconomy.listener;

import net.derfla.quickeconomy.util.FindChest;
import net.derfla.quickeconomy.util.Styles;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.block.Hopper;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * Listener class that handles hopper placement events.
 * Prevents players from placing hoppers that would interfere with locked shop chests.
 */
public class PlayerPlaceHopperListener implements Listener {

    /**
     * Handles the event when a player places a hopper block.
     * Prevents hoppers from being placed on top of locked shop chests to maintain
     * shop integrity and prevent unauthorized item extraction.
     *
     * @param event The BlockPlaceEvent containing information about the hopper placement
     */
    @EventHandler
    public void onPlayerPlaceHopper (BlockPlaceEvent event) {
        if (event.getBlockPlaced() == null)  return;
        if (!event.getBlockPlaced().getType().equals(Material.HOPPER)) return;
        if (!(event.getBlockPlaced().getState() instanceof Hopper)) return;
        if (!FindChest.topLocked(event.getBlockPlaced())) return;
        event.getPlayer().sendMessage(Component.translatable("shop.locked.hopper", Styles.ERRORSTYLE));
        event.setCancelled(true);
    }
}
