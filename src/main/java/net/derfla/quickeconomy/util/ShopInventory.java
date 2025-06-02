package net.derfla.quickeconomy.util;

import net.derfla.quickeconomy.Main;
import net.derfla.quickeconomy.database.Shop;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ShopInventory implements InventoryHolder {

    private String shopOwner;
    private String shopOwner2;
    private double shopCost;
    private Chest shopChest;
    private boolean singleShopItem;

    private final Inventory inventory = Bukkit.createInventory(this, 3 * 9, Component.text("Shop"));
    private final Plugin plugin;

    public ShopInventory(Player player, Chest chest, double cost, String owner, boolean singleItem, String owner2) {
        this.shopOwner = owner;
        this.shopOwner2 = owner2;
        this.shopCost = cost;
        this.shopChest = chest;
        this.singleShopItem = singleItem;
        this.plugin = Main.getInstance();

        if (chest.getBlockInventory().isEmpty()) {
            player.sendMessage(Component.translatable("shop.inventory.empty.player", Styles.INFOSTYLE));
            if (Main.SQLMode) {
                Location chestLoc = chest.getLocation();
                int x_coord = chestLoc.getBlockX();
                int y_coord = chestLoc.getBlockY();
                int z_coord = chestLoc.getBlockZ();
                
                Shop.setShopEmpty(x_coord, y_coord, z_coord)
                    .thenRun(() -> {
                        if (this.plugin.getConfig().getBoolean("shop.emptyShopOwnerMessage")) {
                            notifyOwnerHelper(this.shopOwner, "shop.inventory.empty.owner");
                            notifyOwnerHelper(this.shopOwner2, "shop.inventory.empty.owner");
                        }
                    })
                    .exceptionally(ex -> {
                        this.plugin.getLogger().warning("Failed to set shop as empty in database: " + ex.getMessage());
                        if (this.plugin.getConfig().getBoolean("shop.emptyShopOwnerMessage")) {
                            notifyOwnerHelper(this.shopOwner, "shop.inventory.empty.owner");
                            notifyOwnerHelper(this.shopOwner2, "shop.inventory.empty.owner");
                        }
                        return null;
                    });
            } else {
                if (this.plugin.getConfig().getBoolean("shop.emptyShopOwnerMessage")) {
                    notifyOwnerHelper(this.shopOwner, "shop.inventory.empty.owner");
                    notifyOwnerHelper(this.shopOwner2, "shop.inventory.empty.owner");
                }
            }
            return; 
        } else {
            // Shop is NOT empty when opened, ensure it's marked as such in DB
            if (Main.SQLMode) {
                Location chestLoc = chest.getLocation();
                Shop.unsetShopEmpty(chestLoc.getBlockX(), chestLoc.getBlockY(), chestLoc.getBlockZ())
                    .exceptionally(ex -> {
                        this.plugin.getLogger().warning("Failed to unset shop as empty in database (on open): " + ex.getMessage());
                        return null;
                    });
            }
        }

        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(Component.translatable("shop.inventory.full", Styles.ERRORSTYLE));
            return;
        }

        ItemStack[] chestContent = chest.getBlockInventory().getContents();
        List<ItemStack> newChestContent = new ArrayList<>();
        for (ItemStack item : chestContent) {
            // Ensure only non-null and non-AIR items are added to the GUI display list
            if (item != null && item.getType() != Material.AIR) newChestContent.add(item);
        }
        // Create the filtered array with the correct size
        ItemStack[] filteredChestContent = new ItemStack[newChestContent.size()];
        newChestContent.toArray(filteredChestContent);
        
        inventory.setContents(filteredChestContent);

        BlockOwner.setShopOpen(chest, true);
        player.openInventory(inventory);
    }

    // Helper method to notify shop owners, reducing code duplication
    private void notifyOwnerHelper(String ownerIdentifier, String messageKey) {
        if (ownerIdentifier != null && !ownerIdentifier.isEmpty()) {
            try {
                // Assuming ownerIdentifier is a UUID string that might need untrimming
                UUID actualUUID = UUID.fromString(TypeChecker.untrimUUID(ownerIdentifier)); 
                Player ownerPlayer = Bukkit.getPlayer(actualUUID);
                if (ownerPlayer != null) {
                    ownerPlayer.sendMessage(Component.translatable(messageKey, Styles.INFOSTYLE));
                }
            } catch (IllegalArgumentException e) {
                // Log if the UUID string is invalid
                plugin.getLogger().warning("Encountered an invalid UUID format for shop owner: " + ownerIdentifier);
            }
        }
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

    public Chest getShopChest() {
        return this.shopChest;
    }

    public double getShopCost() {
        return this.shopCost;
    }

    public String getShopOwner() {
        return this.shopOwner;
    }

    public String getShopOwner2() {
        return this.shopOwner2;
    }

    public boolean isSingleItem() {
        return this.singleShopItem;
    }
}
