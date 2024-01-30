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
            if (inventoryType != InventoryType.CHEST) return;
            Boolean cancel = ((BankInventory) event.getInventory().getHolder()).trigger(event.getCurrentItem(), event.getSlot());
            return;

            // Check if it is the ShopInventory
        } else if (event.getInventory().getHolder() instanceof ShopInventory) {
            event.setCancelled(true);
            if (inventoryType != InventoryType.CHEST) return;
            Boolean buy = ((ShopInventory) event.getInventory().getHolder()).trigger(event.getCurrentItem(), event.getSlot());
            if (!buy) return;
            Player player = (Player) event.getWhoClicked();
            float cost = ShopInventory.getShopCost();
            String owner = ShopInventory.getShopOwner();
            Chest chest = ShopInventory.getShopChest();
            if (cost > Balances.getPlayerBalance(player.getName())) {
                player.sendMessage("§cYou can not afford this!");
                return;
            }
            Balances.subPlayerBalance(player.getName(), cost);
            Balances.addPlayerBalance(owner, cost);
            player.getInventory().addItem(event.getCurrentItem());
            chest.getBlockInventory().removeItem(event.getCurrentItem());
            player.closeInventory();
            new ShopInventory(player, chest, cost, owner);
        }
    }

}
