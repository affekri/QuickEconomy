package net.derfla.quickeconomy.util;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

public class BankInventory implements InventoryHolder {

    private Inventory inventory = Bukkit.createInventory(this, 3 * 9, "Bank");
    private Player target;

    static Plugin plugin = Bukkit.getPluginManager().getPlugin("QuickEconomy");


    public BankInventory(Player player) {
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

    public Boolean trigger(ItemStack itemStack, boolean bankInventory) {
        float exchangeRate = getExchangeRate();
        //check if the clicked item is in the BankInventory
        if (bankInventory) {
            switch (itemStack.getType()) {
                case DIAMOND:
                    if (Balances.getPlayerBalance(target.getName()) < exchangeRate){
                        target.closeInventory();
                        target.sendMessage("§cYou do not have enough coins!");
                        return true;
                    }
                    Balances.subPlayerBalance(target.getName(), exchangeRate);
                    target.getInventory().addItem(new ItemStack(Material.DIAMOND));
                    return true;
                case GOLD_INGOT:
                    // Deposit logic
                    target.sendMessage("§fClick diamonds in your inventory to deposit!");
                    return true;
                case GOLD_BLOCK:
                    // Check balance logic
                    target.closeInventory();
                    target.sendMessage("§eYour balance is " + Balances.getPlayerBalance(target.getName()) + " coins!");
                    return true;
            }
        }
        if (itemStack.getType() == Material.DIAMOND) {
            // Deposit logic
            int itemAmount = itemStack.getAmount();
            target.getInventory().removeItem(itemStack);
            Balances.addPlayerBalance(target.getName(), itemAmount * exchangeRate);
            return true;
        }
        return false;
    }

    @Override
    public Inventory getInventory() {
        return this.inventory;
    }

    private static float getExchangeRate() {
        float exchangeRate = 10f;
        if (plugin.getConfig().contains("exchangeRate") && plugin.getConfig().getString("exchangeRate") != null) {
            if (TypeChecker.isFloat(plugin.getConfig().getString("exchangeRate"))) exchangeRate = Float.parseFloat(plugin.getConfig().getString("exchangeRate"));
        } else {
            plugin.getLogger().warning("Could not find exchangeRate in config.yml!");
        }

        return exchangeRate;
    }
}