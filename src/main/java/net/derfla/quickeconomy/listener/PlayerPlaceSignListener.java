package net.derfla.quickeconomy.listener;

import net.derfla.quickeconomy.Main;
import net.derfla.quickeconomy.util.Balances;
import net.derfla.quickeconomy.util.BlockOwner;
import net.derfla.quickeconomy.util.FindChest;
import net.derfla.quickeconomy.util.TypeChecker;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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
    private static final Style bankHeaderStyle = Style.style(NamedTextColor.GOLD, TextDecoration.BOLD);
    private static final Component bankHeader = Component.text("[BANK]").style(bankHeaderStyle);
    private static final Style shopHeaderStyle = Style.style(NamedTextColor.AQUA, TextDecoration.BOLD);
    private static final Component shopHeader = Component.text("[SHOP]").style(shopHeaderStyle);
    private static final Style bodyStyle = Style.style(NamedTextColor.WHITE);

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
            event.line(1, Component.text("Deposit items").style(bodyStyle));
            event.line(2, Component.text("for coins!").style(bodyStyle));
            sign.update();
            player.sendMessage("§eBank created!");
            return;
        } else if (event.line(0).equals(Component.text("[SHOP]"))) {
            if (event.lines().equals(preSign)) return;
            if (!player.hasPermission("quickeconomy.shop.create")) return;
            if (!event.getBlock().getType().toString().contains("WALL")) {
                player.sendMessage("Wrong type of sign for the shop!");
                return;
            }
            if (FindChest.get(sign) == null) {
                player.sendMessage("§cDid not find any chest for this shop!");
                return;
            }

            Chest chest = FindChest.get(sign);
            if (chest == null) {
                player.sendMessage("§cDid not find any chest for this shop!");
                return;
            }
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
            BlockOwner.setShopOpen(chest, false);

            if (event.line(1) == null) return;
            String line1 = TypeChecker.getRawString(event.line(1));
            String line1Error = "§cIncorrect input! The second line should look something like 10.5/Item";
            if (line1 == null || line1.isEmpty()) {
                player.sendMessage(line1Error);
                return;
            }
            if (!line1.contains("/")) {
                player.sendMessage(line1Error);
                return;
            }
            String[] splitLine1 = line1.split("/");
            if (!TypeChecker.isFloat(splitLine1[0])) {
                player.sendMessage(line1Error);
                return;
            }
            float cost = TypeChecker.formatFloat(Float.parseFloat(splitLine1[0]));
            if (cost <= 0.0f) {
                player.sendMessage("§cThe cost must be above 0!");
                return;
            }

            String shopType;
            if (splitLine1.length != 2){
                shopType = "Stack";
            } else if (splitLine1[1].equalsIgnoreCase("item")) {
                shopType = "Item";
            } else shopType = "Stack";
            event.line(0, shopHeader);
            event.line(1, Component.text(cost + "/" + shopType).style(bodyStyle));
            event.line(2, Component.text(player.getName()).style(bodyStyle));
            String line3 = TypeChecker.getRawString(event.line(3));
            if (!line3.isEmpty()) {
                if (Balances.getPlayerBalance(line3) == 0.0f && Bukkit.getPlayer(line3) == null) {
                    event.line(3, Component.empty());
                    player.sendMessage("§cYou tried to add a player that does not seem to exist on this server!");
                } else if (line3.equals(player.getName())) {
                    event.line(3, Component.empty());
                    player.sendMessage("§cYou tried to add yourself as the second player!");
                } else {
                    event.line(3, Component.text(line3).style(bodyStyle));
                    sign.update();
                    player.sendMessage("§eShop created! Half of the income from this shop will go to the player: " + line3);
                    // Lock chest to players
                    BlockOwner.setPlayerLocked(chest, player.getName(), line3);
                    return;
                }
            }
            // Lock chest to player
            BlockOwner.setPlayerLocked(chest, player.getName(), "");
            sign.update();
            player.sendMessage("§eShop created!");
            return;
        }
    }

    public static Component getBankHeaderComponent(){
        return bankHeader;
    }
    public static Component getShopHeaderComponent(){
        return shopHeader;
    }
    public static Style getBodyStyle(){
        return bodyStyle;
    }
}
