package net.derfla.quickeconomy.listener;

import net.derfla.quickeconomy.util.BlockOwner;
import net.derfla.quickeconomy.util.Styles;
import net.derfla.quickeconomy.util.TypeChecker;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.block.Chest;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

public class PlayerBreakChestListener implements Listener {

    @EventHandler
    public void onPlayerBreakChest (BlockBreakEvent event) {
        if (!(event.getBlock().getState() instanceof Chest)) return;
        if (!event.getBlock().getType().equals(Material.CHEST)) return;
        Chest chest = (Chest) event.getBlock().getState();
        if (BlockOwner.isLockedForPlayer(chest, TypeChecker.trimUUID(String.valueOf(event.getPlayer().getUniqueId())))) {
            event.getPlayer().sendMessage(Component.translatable("shop.breakchest.canceled", Styles.ERRORSTYLE));
            event.setCancelled(true);
            return;
        }
    }
}
