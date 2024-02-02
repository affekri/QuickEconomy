package net.derfla.quickeconomy.listener;

import net.derfla.quickeconomy.util.ShopInventory;
import net.derfla.quickeconomy.util.Balances;
import net.derfla.quickeconomy.util.BankInventory;
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
            // Sets owner variable
            String owner = ShopInventory.getShopOwner();
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
            // Gives the paid coins to the shop owner
            Balances.addPlayerBalance(owner, cost);
            // Adds the bought item to the player inventory
            player.getInventory().addItem(boughtItem);
            // Removes the bought item from the shop chest
            chest.getBlockInventory().removeItem(boughtItem);
            // Creates a new shop instance to update without the bought item
            player.closeInventory();
            new ShopInventory(player, chest, cost, owner, singleItem);
        }
    }

}
