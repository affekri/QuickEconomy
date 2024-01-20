package net.derfla.quickeconomy.listeners;

import org.bukkit.Material;
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
        switch (lines[0]) {
            case "[BANK]":
                if(!(player.isOp())) break;
                event.setLine(0, "§6[BANK]");
                event.setLine(1, "§fDeposit items");
                event.setLine(2, "§ffor coins!");
                sign.update();
                player.sendMessage("§eBank created!");
                break;
            case "[SHOP]":
                event.setLine(0, "§a[SHOP]");
                event.setLine(2, "§f" + player.getName());
                sign.update();
                player.sendMessage("Shop created!");
                // TODO
                break;
        }


    }
}
