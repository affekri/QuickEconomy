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
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
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
        String[] lines = sign.getLines();


        if (lines[0].equals("§6[BANK]")) {
            event.setCancelled(true);
            new BankInventory(player);
        } else if (lines[0].equals("§a[SHOP]")) {
            event.setCancelled(true);
            if (FindChest.get(sign) == null) {
                player.sendMessage("Unable to find chest.");
                return;
            }
            Chest chest = FindChest.get(sign);
            String costString = lines[1].replace("§f", "");
            if (!TypeChecker.isFloat(costString)) {
                player.sendMessage("§eThis shop seems to be broken, please alert the owner!");
                return;
            }
            float cost = Float.parseFloat(costString);
            String seller = lines[2].replace("§f", "");
            boolean singleItem = lines[3].replace("§f", "").equalsIgnoreCase("item");

            new ShopInventory(player, chest, cost, seller, singleItem);
        }
    }
}
