package net.derfla.quickeconomy.listener;

import net.derfla.quickeconomy.util.FindChest;
import net.derfla.quickeconomy.util.ShopInventory;
import net.derfla.quickeconomy.util.BankInventory;
import net.derfla.quickeconomy.util.TypeChecker;
import org.bukkit.Material;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

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
        String[] lines = sign.getLines();


        if (lines[0].equals("§6[BANK]")) {
            event.setCancelled(true);
            new BankInventory(player);
        } else if (lines[0].equals("§a[SHOP]")) {
            event.setCancelled(true);
            if (FindChest.get(sign) == null) {
                player.sendMessage("§eThis shop seems to be broken, please alert the owner!");
                return;
            }
            Chest chest = FindChest.get(sign);
            if (chest == null) return;
            String string1 = lines[1].replace("§f", "");
            if (!string1.contains("/")) {
                player.sendMessage("§eThis shop seems to be broken, please alert the owner!");
                return;
            }
            String[] splitLine1 = string1.split("/");
            if (!TypeChecker.isFloat(splitLine1[0])) {
                player.sendMessage("§eThis shop seems to be broken, please alert the owner!");
                return;
            }
            float cost = Float.parseFloat(splitLine1[0]);
            String shopType;
            if (splitLine1[1].equalsIgnoreCase("item")) {
                shopType = "Item";
            } else shopType = "Stack";
            String owner2;
            if (lines[3].contains("§f")){
                owner2 = lines[3].replace("§f", "");
            } else owner2 = "";


            String seller = lines[2].replace("§f", "");
            boolean singleItem = shopType.equalsIgnoreCase("item");

            new ShopInventory(player, chest, cost, seller, singleItem, owner2);
        }
    }
}
