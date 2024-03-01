package net.derfla.quickeconomy.listener;

import net.derfla.quickeconomy.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.Style;
import org.bukkit.Material;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.List;

public class PlayerClickSignListener implements Listener {

    @EventHandler
    public void onPlayerClickSign(PlayerInteractEvent event){
        Player player = event.getPlayer();

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;
        if (!(event.getClickedBlock().getState() instanceof Sign)) return;

        Sign sign = (Sign) event.getClickedBlock().getState();
        List<Component> listLines = sign.getSide(Side.FRONT).lines();
        Component bankHeader = PlayerPlaceSignListener.getBankHeaderComponent();
        Component shopHeader = PlayerPlaceSignListener.getShopHeaderComponent();

        if (listLines.get(0).equals(bankHeader)) {
            event.setCancelled(true);
            if (!player.hasPermission("quickeconomy.bank")) return;
            new BankInventory(player);
        } else if (listLines.get(0).equals(shopHeader)) {
            String seller = TypeChecker.getRawString(listLines.get(2));
            String seller2 = TypeChecker.getRawString(listLines.get(3));
            event.setCancelled(true);
            // Makes owners unable to open their own shop
            if (seller.equals(player.getName()) || seller2.equals(player.getName())) {
                player.sendMessage(Component.translatable("shop.open.own", Styles.ERRORSTYLE));
                return;
            }
            if (!player.hasPermission("quickeconomy.shop")) return;
            if (FindChest.get(sign) == null) {
                player.sendMessage(Component.translatable("shop.broken", Styles.INFOSTYLE));
                return;
            }
            Chest chest = FindChest.get(sign);
            if (chest == null) return;
            if (BlockOwner.isShopOpen(chest)) {
                player.sendMessage(Component.translatable("shop.locked.shopper", Styles.INFOSTYLE));
                return;
            }
            String line1 = TypeChecker.getRawString(listLines.get(1));
            if (!line1.contains("/")) {
                player.sendMessage(Component.translatable("shop.broken", Styles.INFOSTYLE));
                return;
            }
            String[] splitLine1 = line1.split("/");
            if (!TypeChecker.isFloat(splitLine1[0])) {
                player.sendMessage(Component.translatable("shop.broken", Styles.INFOSTYLE));
                return;
            }
            float cost = Float.parseFloat(splitLine1[0]);
            String shopType;
            if (splitLine1[1].equalsIgnoreCase("item")) {
                shopType = "Item";
            } else shopType = "Stack";
            boolean singleItem = shopType.equalsIgnoreCase("item");
            new ShopInventory(player, chest, cost, seller, singleItem, seller2);
        }
    }
}
