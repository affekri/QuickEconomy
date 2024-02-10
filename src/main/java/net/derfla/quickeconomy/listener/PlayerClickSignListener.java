package net.derfla.quickeconomy.listener;

import net.derfla.quickeconomy.util.FindChest;
import net.derfla.quickeconomy.util.ShopInventory;
import net.derfla.quickeconomy.util.BankInventory;
import net.derfla.quickeconomy.util.TypeChecker;
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
        Material blockType = event.getClickedBlock().getType();
        if (!(blockType == Material.OAK_SIGN || blockType == Material.OAK_WALL_SIGN)) return;

        Sign sign = (Sign) event.getClickedBlock().getState();
        List<Component> listLines = sign.getSide(Side.FRONT).lines();
        Component bankHeader = PlayerPlaceSignListener.getBankHeaderComponent();
        Component shopHeader = PlayerPlaceSignListener.getShopHeaderComponent();

        if (listLines.get(0).equals(bankHeader)) {
            event.setCancelled(true);
            new BankInventory(player);
        } else if (listLines.get(0).equals(shopHeader)) {
            String seller = TypeChecker.getRawString(listLines.get(2));
            String seller2 = TypeChecker.getRawString(listLines.get(3));
            // Allows the sellers to edit the shop
            if (seller.equals(player.getName()) || seller2.equals(player.getName())) {
                return;
            }
            event.setCancelled(true);
            if (FindChest.get(sign) == null) {
                player.sendMessage("§eThis shop seems to be broken, please alert the owner!");
                return;
            }
            Chest chest = FindChest.get(sign);
            if (chest == null) return;
            String line1 = TypeChecker.getRawString(listLines.get(1));
            if (!line1.contains("/")) {
                player.sendMessage("§eThis shop seems to be broken, please alert the owner!");
                return;
            }
            String[] splitLine1 = line1.split("/");
            if (!TypeChecker.isFloat(splitLine1[0])) {
                player.sendMessage("§eThis shop seems to be broken, please alert the owner!");
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
