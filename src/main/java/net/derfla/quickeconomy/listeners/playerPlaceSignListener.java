package net.derfla.quickeconomy.listeners;

import net.derfla.quickeconomy.utils.FindChest;
import net.derfla.quickeconomy.utils.typeChecker;
import org.bukkit.Material;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;

public class playerPlaceSignListener implements Listener {

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
            if (!typeChecker.isFloat(lines[1])) return;
            float cost = Float.parseFloat(lines[1]);
            if (cost < 0) return;

            // TODO Check if double chest

            // TODO Check if chest is locked

            // TODO Lock chest to player

            event.setLine(0, "§a[SHOP]");
            event.setLine(1, "§f" + cost);
            event.setLine(2, "§f" + player.getName());
            sign.update();
            player.sendMessage("Shop created!");
            return;
        }
    }
}
