package net.derfla.quickeconomy.util;

import net.derfla.quickeconomy.Main;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class BankInventory implements InventoryHolder {

    private final Inventory inventory = Bukkit.createInventory(this, 3 * 9, Component.text("Bank"));
    private final Player target;

    static Plugin plugin = Main.getInstance();


    public BankInventory(Player player) {
        this.target = player;
        // Creating styles for the text
        Style headerStyle = Style.style(TextDecoration.BOLD, NamedTextColor.GOLD);
        Style bodyStyle = Style.style(TextDecoration.BOLD, NamedTextColor.AQUA);

        // Creating items for inventory
        ItemStack withdrawItem = new ItemStack(Material.DIAMOND);
        ItemMeta withdrawItemMeta = withdrawItem.getItemMeta();
        Component withdrawName = Component.text("Withdraw").style(headerStyle);
        withdrawItemMeta.displayName(withdrawName);
        List<Component> withdrawItemLore = new ArrayList<Component>();
        withdrawItemLore.add(0, Component.text("Click to withdraw diamonds!").style(bodyStyle));
        withdrawItemLore.add(1, Component.text("Right click to withdraw one,").style(bodyStyle));
        withdrawItemLore.add(2, Component.text("left click to withdraw a").style(bodyStyle));
        withdrawItemLore.add(3, Component.text("whole stack!").style(bodyStyle));
        withdrawItemMeta.lore(withdrawItemLore);
        withdrawItem.setItemMeta(withdrawItemMeta);

        ItemStack depositItem = new ItemStack(Material.GOLD_INGOT);
        ItemMeta depositItemMeta = depositItem.getItemMeta();

        Component depositName = Component.text("Deposit").style(headerStyle);
        List<Component> depositItemLore = new ArrayList<Component>();
        depositItemLore.add(0,Component.text("Click diamonds in your").style(bodyStyle));
        depositItemLore.add(1,Component.text("inventory to deposit!").style(bodyStyle));
        depositItemLore.add(2,Component.text("Right click to deposit one").style(bodyStyle));
        depositItemLore.add(3,Component.text("item at a time, left click").style(bodyStyle));
        depositItemLore.add(4,Component.text("to deposit the whole stack!").style(bodyStyle));
        depositItemMeta.displayName(depositName);
        depositItemMeta.lore(depositItemLore);
        depositItem.setItemMeta(depositItemMeta);

        ItemStack balanceItem = new ItemStack(Material.GOLD_BLOCK);
        ItemMeta balanceItemMeta = balanceItem.getItemMeta();
        Component balanceName = Component.text("Balance").style(headerStyle);
        List<Component> balanceItemLore = new ArrayList<Component>();
        balanceItemLore.add(0, Component.text("Click to check").style(bodyStyle));
        balanceItemLore.add(1, Component.text("your balance!").style(bodyStyle));
        balanceItemMeta.lore(balanceItemLore);
        balanceItemMeta.displayName(balanceName);
        balanceItem.setItemMeta(balanceItemMeta);

        // Adding the items created to the inventory
        inventory.setItem(13, withdrawItem);
        inventory.setItem(11, depositItem);
        inventory.setItem(15, balanceItem);
        // Open the inventory for the player
        player.openInventory(inventory);
    }

    public Boolean trigger(ItemStack itemStack, boolean bankInventory, ClickType clickType) {
        float exchangeRate = getExchangeRate();
        //check if the clicked item is in the BankInventory
        if (bankInventory) {
            switch (itemStack.getType()) {
                case DIAMOND:
                    // Withdraw logic
                    if (target.getInventory().firstEmpty() == -1) {
                        target.sendMessage(Component.translatable("bank.inventory.full", Styles.ERRORSTYLE));
                        return false;
                    }
                    int diamondAmount;
                    if (clickType.isLeftClick()) diamondAmount = 64;
                    else diamondAmount = 1;
                    if (Balances.getPlayerBalance(target.getName()) < exchangeRate * diamondAmount){
                        target.sendMessage(Component.translatable("balance.notenough", Styles.ERRORSTYLE));
                        return true;
                    }
                    Balances.subPlayerBalance(target.getName(), exchangeRate * diamondAmount);
                    target.getInventory().addItem(new ItemStack(Material.DIAMOND, diamondAmount));
                    return true;
                case GOLD_INGOT:
                    // Deposit logic
                    target.sendMessage(Component.translatable("bank.inventory.deposit", Styles.INFOSTYLE));
                    return true;
                case GOLD_BLOCK:
                    // Check balance logic
                    target.closeInventory();
                    target.sendMessage(Component.translatable("balance.see", Component.text(Balances.getPlayerBalance(target.getName()))).style(Styles.INFOSTYLE));
                    return true;
            }
        }
        if (!itemStack.getType().equals(Material.DIAMOND)) return false;
        // Deposit logic
        int itemAmount;
        if (clickType.isLeftClick()) {
            itemAmount = itemStack.getAmount();
            target.getInventory().removeItem(itemStack);
        } else  {
            itemAmount = 1;
            target.getInventory().removeItem(new ItemStack(itemStack.getType(), 1));
        }
        Balances.addPlayerBalance(target.getName(), itemAmount * exchangeRate);
        return true;


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
