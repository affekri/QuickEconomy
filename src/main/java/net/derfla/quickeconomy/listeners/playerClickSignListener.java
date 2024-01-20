package net.derfla.quickeconomy.listeners;

import org.bukkit.Material;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import net.derfla.quickeconomy.utils.bankInventory;

public class playerClickSignListener implements Listener {

    @EventHandler
    public void onPlayerClickSign(PlayerInteractEvent event){
        Player player = event.getPlayer();

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (!(event.getClickedBlock().getState() instanceof Sign)) {
            return;
        }
        Material blockType = event.getClickedBlock().getType();
        if (!(blockType == Material.OAK_SIGN || blockType == Material.OAK_WALL_SIGN)) {
            return;
        }
        Sign sign = (Sign) event.getClickedBlock().getState();
        String[] lines = sign.getLines();


        if (lines[0].equals("§6[BANK]")) {
            event.setCancelled(true);
            new bankInventory(player);
        } else if (lines[0].equals("§a[SHOP]")) {
            event.setCancelled(true);
            // TODO
        }
    }
}
