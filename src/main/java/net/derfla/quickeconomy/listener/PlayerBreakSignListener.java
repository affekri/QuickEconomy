package net.derfla.quickeconomy.listener;

import net.derfla.quickeconomy.util.BlockOwner;
import net.derfla.quickeconomy.util.FindChest;
import org.bukkit.Material;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

public class PlayerBreakSignListener implements Listener {

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
            if (FindChest.get(sign) == null) return;
            Chest chest = FindChest.get(sign);
            BlockOwner.setPlayerOwned(chest, player.getName(), false);
            return;
        }
        event.getPlayer().sendMessage("§cYou are not allowed to remove this sign!");
        event.setCancelled(true);
    }
}
