package net.derfla.quickeconomy.listener;

import net.derfla.quickeconomy.util.BlockOwner;
import net.derfla.quickeconomy.util.Styles;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public class PlayerOpenChestListener implements Listener {

    @EventHandler
    public void onPlayerOpenChest(PlayerInteractEvent event){
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;
        if (!(event.getClickedBlock().getState() instanceof Chest)) return;
        if (!event.getClickedBlock().getType().equals(Material.CHEST)) return;
        Chest chest = (Chest) event.getClickedBlock().getState();
        Player player = event.getPlayer();
        // Check if chest is locked
        if (BlockOwner.isLockedForPlayer(chest, player.getName())) {
            player.sendMessage(Component.translatable("shop.locked.chest", Styles.ERRORSTYLE));
            event.setCancelled(true);
            return;
        }
        if (BlockOwner.isShopOpen(chest)) {
            player.sendMessage(Component.translatable("shop.locked.owner", Styles.ERRORSTYLE));
            event.setCancelled(true);
            return;
        }
        if (BlockOwner.isShop(chest)) {
            BlockOwner.setShopOpen(chest, true);
        }
    }
}
