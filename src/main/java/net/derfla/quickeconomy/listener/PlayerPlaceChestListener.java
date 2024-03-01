package net.derfla.quickeconomy.listener;

import net.derfla.quickeconomy.util.BlockOwner;
import net.derfla.quickeconomy.util.FindChest;
import net.derfla.quickeconomy.util.Styles;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.block.Chest;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

public class PlayerPlaceChestListener implements Listener {

    @EventHandler
    public void onPlayerPlaceChest (BlockPlaceEvent event) {
        // Check if making a shop chest to a double
        if (!(event.getBlockPlaced().getState() instanceof Chest)) return;
        if (!event.getBlockPlaced().getType().equals(Material.CHEST)) return;
        Chest chest;
        if (event.getBlockAgainst().getState() instanceof Chest) {
            chest = (Chest) event.getBlockAgainst().getState();
        } else if (FindChest.get((Chest) event.getBlockPlaced().getState()) != null) {
            chest = FindChest.get((Chest) event.getBlockPlaced().getState());
        } else return;
        if (chest == null) return;
        if (BlockOwner.isShop(chest)) {
            event.getPlayer().sendMessage(Component.translatable("shop.double", Styles.ERRORSTYLE));
            event.setCancelled(true);
            return;
        }
    }
}
