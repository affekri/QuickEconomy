package net.derfla.quickeconomy.listener;

import net.derfla.quickeconomy.util.BlockOwner;
import net.derfla.quickeconomy.util.FindChest;
import net.derfla.quickeconomy.util.TypeChecker;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.List;

public class PlayerBreakSignListener implements Listener {

    @EventHandler
    public void onPlayerBreakSign(BlockBreakEvent event) {
        Material blockType = event.getBlock().getType();
        if (!(blockType == Material.OAK_SIGN || blockType == Material.OAK_WALL_SIGN)) {
            return;
        }
        Sign sign = (Sign) event.getBlock().getState();
        List<Component> listLines = sign.getSide(Side.FRONT).lines();
        Component bankHeader = PlayerPlaceSignListener.getBankHeaderComponent();
        Component shopHeader = PlayerPlaceSignListener.getShopHeaderComponent();

        if (!listLines.get(0).equals(bankHeader)) {
            if (!listLines.get(0).equals(shopHeader)) return;
        }

        Player player = event.getPlayer();
        if (listLines.get(0).equals(bankHeader)) {
            if (player.isOp()) return;
        }
        if (listLines.get(0).equals(shopHeader)) {
            if (player.isOp() || TypeChecker.getRawString(listLines.get(2)).equals(player.getName())){
                if (FindChest.get(sign) == null) return;
                Chest chest = FindChest.get(sign);
                BlockOwner.unlockFromPlayer(chest, player.getName());
                return;
            }
        }
        player.sendMessage("§cYou are not allowed to remove this sign!");
        event.setCancelled(true);
    }
}
