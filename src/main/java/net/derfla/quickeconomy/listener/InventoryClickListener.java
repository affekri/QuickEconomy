package net.derfla.quickeconomy.listener;

import net.derfla.quickeconomy.Main;
import net.derfla.quickeconomy.database.Shop;
import net.derfla.quickeconomy.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.Style;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.CompletableFuture;

public class InventoryClickListener implements Listener {

    Style errorStyle = Styles.ERRORSTYLE;
    private static final Plugin plugin = Main.getInstance();

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // Filter out bad events
        if (event.getInventory() == null || event.getCurrentItem() == null) return;
        if (event.getCurrentItem().getType().equals(Material.AIR)) return;

        InventoryType inventoryType = event.getClickedInventory().getType();
        ClickType clickType = event.getClick();

        // Check if the inventory is an instance from this plugin
        // Check if it is the BankInventory
        if (event.getInventory().getHolder() instanceof BankInventory) {
            event.setCancelled(true);
            boolean bankInventory = false;
            if (inventoryType == InventoryType.CHEST) bankInventory = true;
            Boolean cancel = ((BankInventory) event.getInventory().getHolder()).trigger(event.getCurrentItem(), bankInventory, clickType);
            return;

        // Check if it is the ShopInventory
        } else if (event.getInventory().getHolder() instanceof ShopInventory) {
            // Prevents the player from picking up items
            event.setCancelled(true);
            // Returns if the player clicks in the player inventory
            if (inventoryType != InventoryType.CHEST) return;

            ShopInventory shopInventory = (ShopInventory) event.getInventory().getHolder();

            Boolean buy = shopInventory.trigger(event.getCurrentItem(), event.getSlot());
            // Returns if the player does not buy anything
            if (!buy) return;
            // Sets player variable
            Player player = (Player) event.getWhoClicked();

            if (player.getInventory().firstEmpty() == -1) {
                player.sendMessage(Component.translatable("shop.inventory.full", Styles.ERRORSTYLE));
                player.closeInventory();
                return;
            }
            // Sets cost variable
            double cost = shopInventory.getShopCost();
            // Sets owner variables
            String owner = shopInventory.getShopOwner();
            String owner2 = shopInventory.getShopOwner2();
            // Sets the chest variable
            Chest chest = shopInventory.getShopChest();
            // Sets the singleItem variable
            boolean singleItem = shopInventory.isSingleItem();
            // Checks if the player have enough coins
            if (cost > Balances.getPlayerBalance(String.valueOf(player.getUniqueId()))) {
                player.sendMessage(Component.translatable("balance.notenough", errorStyle));
                return;
            }
            ItemStack boughtItem;
            if(singleItem) {
                boughtItem = new ItemStack(event.getCurrentItem().getType());
            } else {
                boughtItem = event.getCurrentItem();
            }
            // Cancels the purchase if it is diamonds; see #21
            if (boughtItem.getType().equals(Material.DIAMOND) || boughtItem.getType().equals(Material.DIAMOND_BLOCK)) {
                player.sendMessage(Component.translatable("shop.buy.diamond", errorStyle));
                return;
            }

            // Handle the transaction asynchronously
            CompletableFuture.runAsync(() -> {
                try {
                    if (owner2.isEmpty()) {
                        // Single owner transaction
                        Balances.executeTransaction("p2p", "purchase", 
                            String.valueOf(player.getUniqueId()), owner, cost, "");
                    } else {
                        // Two owners - execute as a single atomic transaction
                        String playerUUID = String.valueOf(player.getUniqueId());
                        // Sort UUIDs to prevent deadlocks (handled in DatabaseManager)
                        if (playerUUID.compareTo(owner) > 0) {
                            Balances.executeTransaction("p2p", "purchase", owner, playerUUID, -cost/2, "");
                        } else {
                            Balances.executeTransaction("p2p", "purchase", playerUUID, owner, cost/2, "");
                        }
                        
                        if (owner.compareTo(owner2) > 0) {
                            Balances.executeTransaction("p2p", "purchase", owner2, owner, cost/2, "");
                        } else {
                            Balances.executeTransaction("p2p", "purchase", owner, owner2, cost/2, "");
                        }
                    }

                    // Update inventory after successful transaction
                    Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
                        try {
                            // Check if chest is still valid and loaded
                            if (chest == null || !chest.isPlaced() || !chest.getChunk().isLoaded()) {
                                plugin.getLogger().warning("Shop chest is null or not loaded during inventory update for player " + player.getName());
                                // Rollback transaction if chest is invalid
                                String playerUUID = String.valueOf(player.getUniqueId());
                                if (owner2.isEmpty()) {
                                    Balances.executeTransaction("p2p", "rollback", owner, playerUUID, cost, "chest_disappeared");
                                } else {
                                    Balances.executeTransaction("p2p", "rollback", owner, playerUUID, cost/2, "chest_disappeared");
                                    Balances.executeTransaction("p2p", "rollback", owner2, playerUUID, cost/2, "chest_disappeared");
                                }
                                return;
                            }

                            player.getOpenInventory().getTopInventory().removeItem(boughtItem);
                            chest.getBlockInventory().removeItem(boughtItem);
                            player.getInventory().addItem(boughtItem);

                            // Update shop status in database after successful purchase
                            if (Main.SQLMode) {
                                Location chestLoc = chest.getLocation();
                                if (chest.getBlockInventory().isEmpty()) {
                                    Shop.setShopEmpty(chestLoc.getBlockX(), chestLoc.getBlockY(), chestLoc.getBlockZ())
                                        .exceptionally(ex -> {
                                            plugin.getLogger().warning("Failed to set shop as empty after purchase: " + ex.getMessage());
                                            return null;
                                        });
                                } else {
                                    Shop.unsetShopEmpty(chestLoc.getBlockX(), chestLoc.getBlockY(), chestLoc.getBlockZ())
                                        .exceptionally(ex -> {
                                            plugin.getLogger().warning("Failed to unset shop as empty after purchase: " + ex.getMessage());
                                            return null;
                                        });
                                }
                            }

                        } catch (Exception e) {
                            plugin.getLogger().severe("Error updating inventory after purchase for " + player.getName() + ": " + e.getMessage());
                            // Attempt to rollback the transaction if inventory update fails
                            String playerUUID = String.valueOf(player.getUniqueId());
                            if (owner2.isEmpty()) {
                                Balances.executeTransaction("p2p", "rollback", 
                                    owner, playerUUID, cost, "inventory_error");
                            } else {
                                Balances.executeTransaction("p2p", "rollback", 
                                    owner, playerUUID, cost/2, "inventory_error");
                                Balances.executeTransaction("p2p", "rollback", 
                                    owner2, playerUUID, cost/2, "inventory_error");
                            }
                        }
                    });
                } catch (Exception e) {
                    Main.getInstance().getLogger().severe("Error processing shop transaction: " + e.getMessage());
                    Bukkit.getScheduler().runTask(Main.getInstance(), () -> 
                        player.sendMessage(Component.translatable("shop.buy.error", errorStyle)));
                }
            }, Main.getExecutorService());
        }
    }
}
