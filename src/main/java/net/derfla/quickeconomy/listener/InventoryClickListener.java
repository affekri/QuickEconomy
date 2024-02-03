package net.derfla.quickeconomy.listener;

import net.derfla.quickeconomy.util.ShopInventory;
import net.derfla.quickeconomy.util.Balances;
import net.derfla.quickeconomy.util.BankInventory;
import org.bukkit.Material;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;

public class InventoryClickListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // Filter out bad events
        if (event.getInventory() == null || event.getCurrentItem() == null) return;
        if (event.getCurrentItem().getType().equals(Material.AIR)) return;

        InventoryType inventoryType = event.getClickedInventory().getType();


        // Check if the inventory is an instance from this plugin
        // Check if it is the BankInventory
        if (event.getInventory().getHolder() instanceof BankInventory) {
            event.setCancelled(true);
            boolean bankInventory = false;
            if (inventoryType == InventoryType.CHEST) bankInventory = true;
            Boolean cancel = ((BankInventory) event.getInventory().getHolder()).trigger(event.getCurrentItem(), bankInventory);
            return;

        // Check if it is the ShopInventory
        } else if (event.getInventory().getHolder() instanceof ShopInventory) {
            // Prevents the player from picking up items
            event.setCancelled(true);
            // Returns if the player clicks in the player inventory
            if (inventoryType != InventoryType.CHEST) return;
            Boolean buy = ((ShopInventory) event.getInventory().getHolder()).trigger(event.getCurrentItem(), event.getSlot());
            // Returns if the player does not buy anything
            if (!buy) return;
            // Sets player variable
            Player player = (Player) event.getWhoClicked();
            // Sets cost variable
            float cost = ShopInventory.getShopCost();
            // Sets owner variables
            String owner = ShopInventory.getShopOwner();
            String owner2 = ShopInventory.getShopOwner2();
            // Sets the chest variable
            Chest chest = ShopInventory.getShopChest();
            // Sets the singleItem variable
            boolean singleItem = ShopInventory.isSingleItem();
            // Checks if the player have enough coins
            if (cost > Balances.getPlayerBalance(player.getName())) {
                player.sendMessage("§cYou can not afford this!");
                return;
            }
            ItemStack boughtItem;
            if(singleItem) {
                boughtItem = new ItemStack(event.getCurrentItem().getType());
            } else {
                boughtItem = event.getCurrentItem();
            }
            // Removes the cost from the buying player
            Balances.subPlayerBalance(player.getName(), cost);
            // Gives the paid coins to the shop owner or owners
            if (owner2.isEmpty()) {
                Balances.addPlayerBalance(owner, cost);
            }else  {
                Balances.addPlayerBalance(owner, cost / 2);
                Balances.addPlayerBalance(owner2, cost / 2);
            }

            // Removes the bought item from the open shop and the shop chest
            player.getOpenInventory().getTopInventory().remove(boughtItem);
            chest.getBlockInventory().removeItem(boughtItem);
            // Adds the bought item to the player inventory
            player.getInventory().addItem(boughtItem);
        }
    }
}
