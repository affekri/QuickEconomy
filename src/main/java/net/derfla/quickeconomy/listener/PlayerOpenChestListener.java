package net.derfla.quickeconomy.listener;

import org.bukkit.Material;
import org.bukkit.block.Chest;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public class PlayerOpenChestListener implements Listener {

    @EventHandler
    public void onPlayerOpenChest(PlayerInteractEvent event){
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;
        if (!(event.getClickedBlock() instanceof Chest)) return;
        if (!event.getClickedBlock().getType().equals(Material.CHEST)) return;
        Chest chest = (Chest) event.getClickedBlock();
        // TODO Check if chest is locked


    }
}
