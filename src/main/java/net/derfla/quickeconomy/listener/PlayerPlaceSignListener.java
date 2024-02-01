package net.derfla.quickeconomy.listener;

import net.derfla.quickeconomy.util.BlockOwner;
import net.derfla.quickeconomy.util.FindChest;
import net.derfla.quickeconomy.util.TypeChecker;
import org.bukkit.Material;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;

public class PlayerPlaceSignListener implements Listener {

    @EventHandler
    public void onPlayerPlaceSign(SignChangeEvent event) {
        Material blockType = event.getBlock().getType();
        if (!(blockType == Material.OAK_SIGN || blockType == Material.OAK_WALL_SIGN)) {
            return;
        }
        if (event.isCancelled()) {
            return;
        }
        Sign sign = (Sign) event.getBlock().getState();
        String[] lines = event.getLines();
        Player player = event.getPlayer();
        if (lines[0].equals("[BANK]")) {
            if(!(player.isOp())) return;
            event.setLine(0, "§6[BANK]");
            event.setLine(1, "§fDeposit items");
            event.setLine(2, "§ffor coins!");
            sign.update();
            player.sendMessage("§eBank created!");
            return;
        } else if (lines[0].equals("[SHOP]")) {
            if (blockType.equals(Material.OAK_SIGN)) return;
            if (FindChest.get(sign) == null) return;
            Chest chest = FindChest.get(sign);
            if (!TypeChecker.isFloat(lines[1])) return;
            float cost = Float.parseFloat(lines[1]);
            if (cost < 0) return;

            // Check if double chest
            if (FindChest.isDouble(chest)) {
                player.sendMessage("§cYou can only use single chests for shops!");
                return;
            }

            // Check if chest is locked
            if (BlockOwner.isLockedForPlayer(chest, player.getName())) {
                player.sendMessage("§cThis chest is locked to another player!");
                event.setCancelled(true);
                return;
            }
            if (BlockOwner.isLocked(chest)) {
                player.sendMessage("§cYou already have a shop on this chest!");
                event.setCancelled(true);
                return;
            }

            // Lock chest to player
            BlockOwner.setPlayerOwned(chest, player.getName(), true);

            event.setLine(0, "§a[SHOP]");
            event.setLine(1, "§f" + cost);
            event.setLine(2, "§f" + player.getName());
            sign.update();
            player.sendMessage("Shop created!");
            return;
        }
    }
}
