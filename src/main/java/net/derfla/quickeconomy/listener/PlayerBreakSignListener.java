package net.derfla.quickeconomy.listener;

import net.derfla.quickeconomy.util.*;
import net.kyori.adventure.text.Component;
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
        if (!(event.getBlock().getState() instanceof Sign)) {
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
            if (player.hasPermission("quickeconomy.bank.destroy")) return;
        }
        if (listLines.get(0).equals(shopHeader)) {
            if (player.hasPermission("quickeconomy.shop.destroyall") ||
                    Balances.getUUID(TypeChecker.getRawString(listLines.get(2))).equals(TypeChecker.trimUUID(String.valueOf(player.getUniqueId())))){
                if (FindChest.get(sign) == null) return;
                Chest chest = FindChest.get(sign);
                BlockOwner.unlockFromPlayer(chest, player.getName());
                return;
            }
        }
        player.sendMessage(Component.translatable("shop.breaksign.canceled", Styles.ERRORSTYLE));
        event.setCancelled(true);
    }
}
