package net.derfla.quickeconomy.listener;

import net.derfla.quickeconomy.util.FindChest;
import org.bukkit.Material;
import org.bukkit.block.Hopper;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

public class PlayerPlaceHopperListener implements Listener {

    @EventHandler
    public void onPlayerPlaceHopper (BlockPlaceEvent event) {
        if (event.getBlockPlaced() == null)  return;
        if (!event.getBlockPlaced().getType().equals(Material.HOPPER)) return;
        if (!(event.getBlockPlaced().getState() instanceof Hopper)) return;
        if (!FindChest.topLocked(event.getBlockPlaced())) return;
        event.getPlayer().sendMessage("§cYou can't place hoppers under shops!");
        event.setCancelled(true);
    }
}
