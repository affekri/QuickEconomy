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
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.plugin.Plugin;

public class PlayerPlaceSignListener implements Listener {

    static Plugin plugin = Main.getInstance();
    private static final Style bankHeaderStyle = Style.style(NamedTextColor.GOLD, TextDecoration.BOLD);
    private static final Component bankHeader = Component.text("[BANK]").style(bankHeaderStyle);
    private static final Style shopHeaderStyle = Style.style(NamedTextColor.AQUA, TextDecoration.BOLD);
    private static final Component shopHeader = Component.text("[SHOP]").style(shopHeaderStyle);
    private static final Style bodyStyle = Style.style(NamedTextColor.WHITE);

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
        Player player = event.getPlayer();

        if (event.line(0).equals(Component.text("[BANK]"))) {
            if (!player.isOp()) return;
            event.line(0, bankHeader);
            event.line(1, Component.text("Deposit items").style(bodyStyle));
            event.line(2, Component.text("for coins!").style(bodyStyle));
            sign.update();
            player.sendMessage("§eBank created!");
            return;
        } else if (event.line(0).equals(Component.text("[SHOP]"))) {
            if (blockType.equals(Material.OAK_SIGN)) return;
            if (FindChest.get(sign) == null) return;
            Chest chest = FindChest.get(sign);
            if (chest == null) return;
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
            if (event.line(1) == null) return;
            String line1 = TypeChecker.getRawString(event.line(1));
            String line1Error = "§cIncorrect input! The second line should look something like 10.5/Item";
            if (line1 == null || line1.equals("")) {
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
            float cost = Float.parseFloat(splitLine1[0]);
            if (cost < 0) {
                player.sendMessage("§cThe cost must be above 0!");
                return;
            }
            String shopType;
            if (splitLine1[1].equalsIgnoreCase("item")) {
                shopType = "Item";
            } else shopType = "Stack";
            // Lock chest to player
            BlockOwner.setPlayerOwned(chest, player.getName(), true);
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
                    return;
                }
            }
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
