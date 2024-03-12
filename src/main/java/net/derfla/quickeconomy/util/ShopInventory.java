package net.derfla.quickeconomy.util;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class ShopInventory implements InventoryHolder {

    private static String shopOwner;
    private static String shopOwner2;
    private static float shopCost;
    private static  Chest shopChest;
    private static boolean singleShopItem;

    private final Inventory inventory = Bukkit.createInventory(this, 3 * 9, Component.text("Bank"));
    final Player target;


    public ShopInventory(Player player, Chest chest, float cost, String owner, boolean singleItem, String owner2) {
        this.target = player;
        // Check if shop is empty
        if (chest.getBlockInventory().isEmpty()) {
            player.sendMessage(Component.translatable("shop.inventory.empty", Styles.INFOSTYLE));
            // TODO Add message to owner
            return;
        }
        // Check if the player inventory is full
        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(Component.translatable("shop.inventory.full", Styles.ERRORSTYLE));
            return;
        }
        shopOwner = owner;
        shopCost = cost;
        shopChest = chest;
        singleShopItem = singleItem;
        shopOwner2 = owner2;

        // Sorts the chest inventory
        ItemStack[] chestContent = chest.getBlockInventory().getContents();
        List<ItemStack> newChestContent = new ArrayList<>();
        for (ItemStack item : chestContent){
            if (item != null) newChestContent.add(item);
        }
        ItemStack[] filteredChestContent = new ItemStack[chestContent.length];
        newChestContent.toArray(filteredChestContent);
        chest.getBlockInventory().setContents(filteredChestContent);
        chest.update();

        inventory.setContents(filteredChestContent);

        BlockOwner.setShopOpen(chest, true);
        player.openInventory(inventory);
    }

    public Boolean trigger(ItemStack itemStack, int slot) {
        if (slot > 26) return false;
        if (itemStack == null) return false;
        if (itemStack.getType().equals(Material.AIR)) return false;


        return true;
    }

    @Override
    public Inventory getInventory() {
        return this.inventory;
    }

    public static Chest getShopChest() {return shopChest; }
    public static float getShopCost(){
        return shopCost;
    }

    public static String getShopOwner() {
        return shopOwner;
    }

    public static String getShopOwner2() {
        return shopOwner2;
    }

    public static boolean isSingleItem() {return singleShopItem;}
}
