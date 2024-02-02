package net.derfla.quickeconomy.util;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public class ShopInventory implements InventoryHolder {

    private static String shopOwner;
    private static float shopCost;
    private static  Chest shopChest;
    private static boolean singleShopItem;

    private Inventory inventory = Bukkit.createInventory(this, 3 * 9, "Shop");
    private Player target;


    public ShopInventory(Player player, Chest chest, float cost, String owner, boolean singleItem) {
        this.target = player;
        if (chest.getBlockInventory().isEmpty()) {
            player.sendMessage("§eThis shop is currently empty!");
            //TODO Add message to owner
            return;
        }
        shopOwner = owner;
        shopCost = cost;
        shopChest = chest;
        singleShopItem = singleItem;

        ItemStack[] shopContent =  chest.getBlockInventory().getContents();
        inventory.setContents(shopContent);


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
    public static boolean isSingleItem() {return singleShopItem;}
}
