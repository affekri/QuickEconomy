package net.derfla.quickeconomy.listener;

import net.derfla.quickeconomy.Main;
import net.derfla.quickeconomy.util.*;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.plugin.Plugin;

import java.util.List;

public class PlayerPlaceSignListener implements Listener {

    static Plugin plugin = Main.getInstance();
    private static final Component bankHeader = Component.text("[BANK]").style(Styles.BANKHEADER);
    private static final Component shopHeader = Component.text("[SHOP]").style(Styles.SHOPHEADER);

    @EventHandler
    public void onPlayerPlaceSign(SignChangeEvent event) {
        if (!(event.getBlock().getState() instanceof Sign)) {
            return;
        }
        if (event.getBlock().getType().toString().contains("HANGING")) {
            return;
        }
        if (event.isCancelled()) {
            return;
        }
        Sign sign = (Sign) event.getBlock().getState();
        List<Component> preSign = sign.getSide(Side.FRONT).lines();
        Player player = event.getPlayer();

        if (!event.line(0).equals(Component.text("[BANK]")) && !event.line(0).equals(Component.text("[SHOP]"))) return;

        if (event.line(0).equals(Component.text("[BANK]"))) {
            if (!player.hasPermission("quickeconomy.bank.create")) return;
            event.line(0, bankHeader);
            event.line(1, Component.text("Deposit items").style(Styles.BODY));
            event.line(2, Component.text("for coins!").style(Styles.BODY));
            sign.update();
            player.sendMessage(Component.translatable("bank.created", Styles.INFOSTYLE));
            return;
        } else if (event.line(0).equals(Component.text("[SHOP]"))) {
            if (event.lines().equals(preSign)) return;
            if (!player.hasPermission("quickeconomy.shop.create")) return;
            if (!event.getBlock().getType().toString().contains("WALL")) return;
            if (FindChest.get(sign) == null) {
                player.sendMessage(Component.translatable("shop.nochest", Styles.ERRORSTYLE));
                return;
            }

            Chest chest = FindChest.get(sign);
            if (chest == null) {
                player.sendMessage(Component.translatable("shop.chest.null", Styles.ERRORSTYLE));
                return;
            }
            // Check if double chest
            if (FindChest.isDouble(chest)) {
                player.sendMessage(Component.translatable("shop.chest.notsingle", Styles.ERRORSTYLE));
                return;
            }

            // Check if chest is locked
            if (BlockOwner.isLockedForPlayer(chest, player.getName())) {
                player.sendMessage(Component.translatable("shop.locked.chest", Styles.ERRORSTYLE));
                event.setCancelled(true);
                return;
            }
            if (BlockOwner.isLocked(chest)) {
                player.sendMessage(Component.translatable("shop.chest.alreadyshop", Styles.ERRORSTYLE));
                event.setCancelled(true);
                return;
            }
            BlockOwner.setShopOpen(chest, false);

            if (event.line(1) == null) return;
            String line1 = TypeChecker.getRawString(event.line(1));
            if (line1 == null || line1.isEmpty()) {
                player.sendMessage(Component.translatable("shop.sign.line1error", Styles.ERRORSTYLE));
                return;
            }
            if (!line1.contains("/")) {
                player.sendMessage(Component.translatable("shop.sign.line1error", Styles.ERRORSTYLE));
                return;
            }
            String[] splitLine1 = line1.split("/");
            if (!TypeChecker.isFloat(splitLine1[0])) {
                player.sendMessage(Component.translatable("shop.sign.line1error", Styles.ERRORSTYLE));
                return;
            }
            float cost = TypeChecker.formatFloat(Float.parseFloat(splitLine1[0]));
            if (cost <= 0.0f) {
                player.sendMessage(Component.translatable("shop.sign.costtolow", Styles.ERRORSTYLE));
                return;
            }

            String shopType;
            if (splitLine1.length != 2){
                shopType = "Stack";
            } else if (splitLine1[1].equalsIgnoreCase("item")) {
                shopType = "Item";
            } else shopType = "Stack";
            event.line(0, shopHeader);
            event.line(1, Component.text(cost + "/" + shopType).style(Styles.BODY));
            event.line(2, Component.text(player.getName()).style(Styles.BODY));
            String line3 = TypeChecker.getRawString(event.line(3));
            if (!line3.isEmpty()) {
                if (Balances.getPlayerBalance(line3) == 0.0f && Bukkit.getPlayer(line3) == null) {
                    event.line(3, Component.empty());
                    player.sendMessage(Component.translatable("player.notexists", Styles.ERRORSTYLE));
                } else if (line3.equals(player.getName())) {
                    event.line(3, Component.empty());
                    player.sendMessage(Component.translatable("shop.sign.self.second", Styles.ERRORSTYLE));
                } else {
                    event.line(3, Component.text(line3).style(Styles.BODY));
                    sign.update();
                    player.sendMessage(Component.translatable("shop.created.split", Component.text(line3)).style(Styles.INFOSTYLE));
                    // Lock chest to players
                    BlockOwner.setPlayerLocked(chest, player.getName(), line3);
                    return;
                }
            }
            // Lock chest to player
            BlockOwner.setPlayerLocked(chest, player.getName(), "");
            sign.update();
            player.sendMessage(Component.translatable("shop.created", Styles.INFOSTYLE));
            return;
        }
    }

    public static Component getBankHeaderComponent(){
        return bankHeader;
    }
    public static Component getShopHeaderComponent(){
        return shopHeader;
    }
}
