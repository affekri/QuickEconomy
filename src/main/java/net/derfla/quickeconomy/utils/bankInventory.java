package net.derfla.quickeconomy.utils;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class bankInventory implements InventoryHolder {

    private Inventory inventory = Bukkit.createInventory(this, 3 * 9, "Bank");
    private Player target;


    public bankInventory(Player player) {
        this.target = player;
        // Creating items for inventory
        ItemStack withdrawItem = new ItemStack(Material.DIAMOND);
        ItemMeta withdrawItemMeta = withdrawItem.getItemMeta();
        Component withdrawComponent = Component.text("Withdraw");
        withdrawItemMeta.displayName(withdrawComponent);
        withdrawItem.setItemMeta(withdrawItemMeta);

        ItemStack depositItem = new ItemStack(Material.GOLD_INGOT);
        ItemMeta depositItemMeta = depositItem.getItemMeta();
        Component depositComponent = Component.text("Deposit");
        depositItemMeta.displayName(depositComponent);
        depositItem.setItemMeta(depositItemMeta);

        ItemStack balanceItem = new ItemStack(Material.GOLD_BLOCK);
        ItemMeta balanceItemMeta = balanceItem.getItemMeta();
        Component balanceComponent = Component.text("Balance");
        balanceItemMeta.displayName(balanceComponent);
        balanceItem.setItemMeta(balanceItemMeta);

        // Adding the items created to the inventory
        inventory.setItem(13, withdrawItem);
        inventory.setItem(11, depositItem);
        inventory.setItem(15, balanceItem);
        // Open the inventory for the player
        player.openInventory(inventory);
    }

    public Boolean trigger(ItemStack itemStack, int slot) {
        //check if the clicked item is stone
        if (itemStack.getType() == Material.DIAMOND && slot == 13) {
            // Withdraw logic
            if (balances.getPlayerBalance(target.getName()) < 10f){
                target.closeInventory();
                target.sendMessage("§cYou do not have enough coins!");
                return true;
            }
            balances.subPlayerBalance(target.getName(), 10f);
            target.getInventory().addItem(new ItemStack(Material.DIAMOND));
            return true;
        } else if (itemStack.getType() == Material.GOLD_INGOT && slot == 11) {
            // Deposit logic
            target.sendMessage("§fClick diamonds in your inventory to deposit!");
            return true;
        } else if (itemStack.getType() == Material.DIAMOND) {
            // Deposit logic
            target.getInventory().removeItem(new ItemStack(Material.DIAMOND));
            balances.addPlayerBalance(target.getName(), 10f);
        } else if (itemStack.getType() == Material.GOLD_BLOCK) {
            // Check balance logic
            target.closeInventory();
            target.sendMessage("§eYour balance is " + balances.getPlayerBalance(target.getName()) + " coins!");
        }
        //don't cancel the event
        return false;
    }

    @Override
    public Inventory getInventory() {
        return this.inventory;
    }
}
