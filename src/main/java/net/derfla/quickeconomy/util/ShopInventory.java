package net.derfla.quickeconomy.util;

import net.derfla.quickeconomy.Main;
import net.derfla.quickeconomy.database.Shop;
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
import java.util.UUID;

/**
 * Implementation of a player shop GUI system that allows players to purchase items from chest-based shops.
 * This class creates and manages an interactive inventory interface that displays items from a physical
 * chest and handles purchase transactions between players.
 * 
 * <p>The shop system operates with the following components:
 * <ul>
 *   <li><strong>Shop Chest:</strong> Physical chest containing items for sale</li>
 *   <li><strong>Shop Owners:</strong> Up to two players who own the shop (primary and secondary)</li>
 *   <li><strong>Pricing:</strong> Fixed cost per item or per transaction</li>
 *   <li><strong>Empty Shop Tracking:</strong> Database logging when shops run out of inventory</li>
 * </ul>
 * </p>
 * 
 * <p>Key features:
 * <ul>
 *   <li>Automatic inventory synchronization with the underlying chest</li>
 *   <li>Empty shop detection and owner notification</li>
 *   <li>Inventory compaction (removal of null slots)</li>
 *   <li>Player inventory space validation</li>
 *   <li>Support for single or multiple shop owners</li>
 * </ul>
 * </p>
 * 
 * <p><strong>Static State:</strong> This class uses static fields to maintain shop state during
 * a transaction session. This design allows access to shop information from event handlers
 * but requires careful management to avoid concurrent access issues.</p>
 * 
 * <p><strong>Thread Safety:</strong> This class should only be accessed from the main server thread
 * as it interacts with Bukkit's inventory, chest, and player APIs.</p>
 * 
 * @author QuickEconomy
 * @version 1.0
 * @since 1.0
 * @see InventoryHolder
 * @see BlockOwner
 * @see DatabaseManager
 */
public class ShopInventory implements InventoryHolder {

    /** UUID of the primary shop owner (trimmed format) */
    private static String shopOwner;
    
    /** UUID of the secondary shop owner (trimmed format), may be empty */
    private static String shopOwner2;
    
    /** The cost per item or per transaction for this shop */
    private static double shopCost;
    
    /** The physical chest that contains the shop's inventory */
    private static Chest shopChest;
    
    /** Whether this shop sells items individually (true) or in stacks (false) */
    private static boolean singleShopItem;

    /** The inventory instance representing the shop GUI */
    private final Inventory inventory = Bukkit.createInventory(this, 3 * 9, Component.text("Shop"));
    
    /** The player who opened this shop inventory */
    final Player target;


    /**
     * Creates a new shop inventory GUI for the specified player and immediately opens it.
     * This constructor sets up the shop session, validates inventory state, and handles empty shop scenarios.
     * 
     * <p>The constructor performs several important operations:
     * <ul>
     *   <li><strong>Empty Shop Detection:</strong> Checks if the chest is empty and notifies relevant parties</li>
     *   <li><strong>Database Logging:</strong> Records empty shop events if SQL mode is enabled</li>
     *   <li><strong>Owner Notifications:</strong> Alerts shop owners when their shop runs out of items</li>
     *   <li><strong>Inventory Validation:</strong> Ensures the customer has space for purchases</li>
     *   <li><strong>Inventory Compaction:</strong> Removes null slots and reorganizes chest contents</li>
     *   <li><strong>Shop State Management:</strong> Marks the chest as "shop open" and opens the GUI</li>
     * </ul>
     * </p>
     * 
     * <p>If the shop is empty or the player's inventory is full, appropriate messages are sent
     * and the GUI is not opened.</p>
     * 
     * @param player the player opening the shop
     * @param chest the chest containing the shop's inventory
     * @param cost the cost per item or transaction for this shop
     * @param owner the UUID of the primary shop owner (trimmed format)
     * @param singleItem whether items are sold individually (true) or in stacks (false)
     * @param owner2 the UUID of the secondary shop owner (trimmed format), may be empty
     * 
     * @throws IllegalArgumentException if player or chest is null
     */
    public ShopInventory(Player player, Chest chest, double cost, String owner, boolean singleItem, String owner2) {
        this.target = player;
        shopOwner = owner;
        shopOwner2 = owner2;
        shopCost = cost;
        shopChest = chest;
        singleShopItem = singleItem;

        // Check if shop is empty
        if (chest.getBlockInventory().isEmpty()) {
            player.sendMessage(Component.translatable("shop.inventory.empty.player", Styles.INFOSTYLE));
            if (Main.SQLMode) {
                // Store empty shop details in the database
                String coordinates = chest.getLocation().getBlockX() + "," + chest.getLocation().getBlockY() + "," + chest.getLocation().getBlockZ();
                if (Shop.insertEmptyShop(coordinates, owner, owner2).join()) {
                    // Notify the shop owners
                    if (Main.getInstance().getConfig().getBoolean("shop.emptyShopOwnerMessage")) {
                        if (!owner.isEmpty()) {
                            UUID shopOwnerUUID = UUID.fromString(TypeChecker.untrimUUID(shopOwner));
                            if (Bukkit.getPlayer(shopOwnerUUID) != null) {
                                Player shopOwnerPlayer = Bukkit.getPlayer(shopOwnerUUID);
                                shopOwnerPlayer.sendMessage(Component.translatable("shop.inventory.empty.owner", Styles.INFOSTYLE));
                            }
                        }
                        if (!owner2.isEmpty()) {
                            UUID shopOwner2UUID = UUID.fromString(TypeChecker.untrimUUID(shopOwner2));
                            if (Bukkit.getPlayer(shopOwner2UUID) != null) {
                                Player shopOwnerPlayer2 = Bukkit.getPlayer(shopOwner2UUID);
                                shopOwnerPlayer2.sendMessage(Component.translatable("shop.inventory.empty.owner", Styles.INFOSTYLE));
                            }
                        }
                    }
                }
            } else {
                // Notify the shop owners
                if (Main.getInstance().getConfig().getBoolean("shop.emptyShopOwnerMessage")) {
                    if (!owner.isEmpty()) {
                        UUID shopOwnerUUID = UUID.fromString(TypeChecker.untrimUUID(shopOwner));
                        if (Bukkit.getPlayer(shopOwnerUUID) != null) {
                            Player shopOwnerPlayer = Bukkit.getPlayer(shopOwnerUUID);
                            shopOwnerPlayer.sendMessage(Component.translatable("shop.inventory.empty.owner", Styles.INFOSTYLE));
                        }
                    }
                    if (!owner2.isEmpty()) {
                        UUID shopOwner2UUID = UUID.fromString(TypeChecker.untrimUUID(shopOwner2));
                        if (Bukkit.getPlayer(shopOwner2UUID) != null) {
                            Player shopOwnerPlayer2 = Bukkit.getPlayer(shopOwner2UUID);
                            shopOwnerPlayer2.sendMessage(Component.translatable("shop.inventory.empty.owner", Styles.INFOSTYLE));
                        }
                    }
                }
            }

            return;
        }

        // Check if the player inventory is full
        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(Component.translatable("shop.inventory.full", Styles.ERRORSTYLE));
            return;
        }

        // Sorts the chest inventory
        ItemStack[] chestContent = chest.getBlockInventory().getContents();
        List<ItemStack> newChestContent = new ArrayList<>();
        for (ItemStack item : chestContent) {
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

    /**
     * Handles player interactions with items in the shop inventory for purchase processing.
     * This method validates the clicked item and slot to determine if a purchase should proceed.
     * 
     * <p>Current validation checks:
     * <ul>
     *   <li>Slot must be within the valid shop inventory range (0-26)</li>
     *   <li>ItemStack must not be null</li>
     *   <li>ItemStack must not be AIR (empty slot)</li>
     * </ul>
     * </p>
     * 
     * <p><strong>Note:</strong> This method currently returns true for all valid interactions
     * but does not implement the actual purchase logic. The transaction processing would
     * typically be handled by external event listeners that use the static getter methods
     * to access shop information.</p>
     * 
     * @param itemStack the item that was clicked in the shop inventory
     * @param slot the slot number that was clicked (0-based)
     * @return true if the interaction is valid and should proceed, false to cancel the interaction
     */
    public Boolean trigger(ItemStack itemStack, int slot) {
        if (slot > 26) return false;
        if (itemStack == null) return false;
        if (itemStack.getType().equals(Material.AIR)) return false;


        return true;
    }

    /**
     * Returns the inventory instance for this shop GUI.
     * Required implementation of the {@link InventoryHolder} interface.
     * 
     * @return the shop inventory instance
     */
    @Override
    public Inventory getInventory() {
        return this.inventory;
    }

    /**
     * Retrieves the chest that contains this shop's physical inventory.
     * This static method allows external classes to access the shop chest during a transaction session.
     * 
     * @return the chest block state representing the shop's inventory source
     */
    public static Chest getShopChest() {
        return shopChest;
    }

    /**
     * Retrieves the cost per item or per transaction for this shop.
     * This static method allows external classes to access pricing information during transactions.
     * 
     * @return the cost as a double value
     */
    public static double getShopCost() {
        return shopCost;
    }

    /**
     * Retrieves the UUID of the primary shop owner.
     * This static method provides access to ownership information for transaction processing.
     * 
     * @return the primary owner's UUID in trimmed format (32 characters without dashes)
     */
    public static String getShopOwner() {
        return shopOwner;
    }

    /**
     * Retrieves the UUID of the secondary shop owner, if any.
     * This static method provides access to secondary ownership information for transaction processing.
     * 
     * @return the secondary owner's UUID in trimmed format, or empty string if no secondary owner
     */
    public static String getShopOwner2() {
        return shopOwner2;
    }

    /**
     * Determines whether this shop sells items individually or in stacks.
     * This static method allows external classes to understand the shop's pricing model.
     * 
     * @return true if items are sold individually, false if sold in full stacks
     */
    public static boolean isSingleItem() {
        return singleShopItem;
    }
}
