package net.derfla.quickeconomy.listeners;

import net.derfla.quickeconomy.utils.bankInventory;
import org.bukkit.Material;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

public class playerBreakSignListener implements Listener {

    @EventHandler
    public void onPlayerBreakSign(BlockBreakEvent event) {
        Material blockType = event.getBlock().getType();
        if (!(blockType == Material.OAK_SIGN || blockType == Material.OAK_WALL_SIGN)) {
            return;
        }
        Sign sign = (Sign) event.getBlock().getState();
        String[] lines = sign.getLines();


        if (!(lines[0].equals("§6[BANK]") || lines[0].equals("§a[SHOP]"))) {
            return;
        }
        Player player = event.getPlayer();
        if (player.isOp() || lines[2].equals("§f" + player.getName())){
            return;
        }
        event.setCancelled(true);
    }
}
