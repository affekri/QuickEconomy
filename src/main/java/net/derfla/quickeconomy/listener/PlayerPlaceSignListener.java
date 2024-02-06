package net.derfla.quickeconomy.listener;

import net.derfla.quickeconomy.util.Balances;
import net.derfla.quickeconomy.util.BlockOwner;
import net.derfla.quickeconomy.util.FindChest;
import net.derfla.quickeconomy.util.TypeChecker;
import org.bukkit.Bukkit;
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
        } else if (lines[0].equalsIgnoreCase("[SHOP]")) {
            if (blockType.equals(Material.OAK_SIGN)) return;
            if (FindChest.get(sign) == null) return;
            Chest chest = FindChest.get(sign);
            if (chest == null) return;
            if (!lines[1].contains("/")) {
                player.sendMessage("§cIncorrect input! The second line should look something like 10.5/Item");
                event.setCancelled(true);
                return;
            }
            String[] splitLine1 = lines[1].split("/");
            if (!TypeChecker.isFloat(splitLine1[0])) {
                player.sendMessage("§cIncorrect input! The second line should look something like 10.5/Item");
                event.setCancelled(true);
                return;
            }
            float cost = Float.parseFloat(splitLine1[0]);
            if (cost < 0) {
                player.sendMessage("§cThe cost must be above 0!");
                event.setCancelled(true);
                return;
            }
            String shopType;
            if (splitLine1[1].equalsIgnoreCase("item")) {
                shopType = "Item";
            } else shopType = "Stack";

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
            event.setLine(1, "§f" + cost + "/" + shopType);
            event.setLine(2, "§f" + player.getName());
            if (!lines[3].isEmpty()){
                if (Balances.getPlayerBalance(lines[3]) == 0.0f && Bukkit.getPlayer(lines[3]) == null) {
                    event.setLine(3, "");
                    player.sendMessage("§cYou tried to add a player that does not seem to exist on this server!");
                } else if (lines[3].equals(player.getName())) {
                    event.setLine(3, "");
                    player.sendMessage("§cYou tried to add yourself as the second player!");
                } else {
                    event.setLine(3, "§f" + lines[3]);
                    sign.update();
                    player.sendMessage("§eShop created! Half of the income from this shop will go to the player: " + lines[3]);
                    return;
                }
            }
            sign.update();
            player.sendMessage("§eShop created!");
            return;
        }
    }
}
