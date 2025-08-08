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
import java.util.List;

/**
 * Implementation of a bank GUI system that allows players to convert between currency and physical diamonds.
 * This class creates and manages an interactive inventory interface for banking operations including
 * deposits, withdrawals, and balance checking.
 * 
 * <p>The bank system operates on an exchange rate between in-game currency and diamonds:
 * <ul>
 *   <li><strong>Withdraw:</strong> Convert currency to diamonds (deduct from balance, give diamonds)</li>
 *   <li><strong>Deposit:</strong> Convert diamonds to currency (remove diamonds, add to balance)</li>
 *   <li><strong>Balance Check:</strong> Display current player balance</li>
 * </ul>
 * </p>
 * 
 * <p>The GUI consists of a 3x9 inventory with three main action items:
 * <ul>
 *   <li><strong>Diamond (Slot 13):</strong> Withdraw operation - left click for stack (64), right click for single</li>
 *   <li><strong>Gold Ingot (Slot 11):</strong> Deposit instruction item</li>
 *   <li><strong>Gold Block (Slot 15):</strong> Balance check operation</li>
 * </ul>
 * </p>
 * 
 * <p>For deposits, players click diamonds in their inventory rather than the deposit item itself.
 * The exchange rate is configurable through the plugin's config.yml file.</p>
 * 
 * <p><strong>Thread Safety:</strong> This class should only be accessed from the main server thread
 * as it interacts with Bukkit's inventory and player APIs.</p>
 * 
 * @author QuickEconomy
 * @version 1.0
 * @since 1.0
 * @see InventoryHolder
 * @see Balances
 */
public class BankInventory implements InventoryHolder {

    /** The inventory instance representing the bank GUI */
    private final Inventory inventory = Bukkit.createInventory(this, 3 * 9, Component.text("Bank"));
    
    /** The player who opened this bank inventory */
    private final Player target;

    static Plugin plugin = Main.getInstance();


    /**
     * Creates a new bank inventory GUI for the specified player and immediately opens it.
     * This constructor sets up all the interactive elements including withdraw, deposit instruction,
     * and balance check items with appropriate styling and tooltips.
     * 
     * <p>The constructor performs the following setup:
     * <ul>
     *   <li>Creates styled GUI items with custom names and lore text</li>
     *   <li>Places items in specific inventory slots for optimal layout</li>
     *   <li>Automatically opens the inventory for the player</li>
     * </ul>
     * </p>
     * 
     * @param player the player for whom to create and open the bank inventory
     * @throws IllegalArgumentException if player is null
     */
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

    /**
     * Handles player interactions with the bank inventory, processing both GUI clicks and diamond deposits.
     * This method determines the appropriate banking operation based on the clicked item and click type.
     * 
     * <p>Supported operations:
     * <ul>
     *   <li><strong>Withdraw (Diamond click):</strong> Left click = 64 diamonds, Right click = 1 diamond</li>
     *   <li><strong>Deposit Instruction (Gold Ingot click):</strong> Shows help message for depositing</li>
     *   <li><strong>Balance Check (Gold Block click):</strong> Displays current balance and closes GUI</li>
     *   <li><strong>Diamond Deposit:</strong> Converts player's diamonds to currency</li>
     * </ul>
     * </p>
     * 
     * <p>The method validates player inventory space for withdrawals and sufficient balance before
     * processing transactions. All currency operations use the configured exchange rate.</p>
     * 
     * @param itemStack the item that was clicked
     * @param bankInventory true if the click occurred within the bank GUI, false if in player inventory
     * @param clickType the type of click (left, right, etc.)
     * @return true if the interaction was handled successfully, false if it should be cancelled or ignored
     * 
     * @see ClickType
     * @see Balances#executeTransaction(String, String, String, String, double, String)
     */
    public Boolean trigger(ItemStack itemStack, boolean bankInventory, ClickType clickType) {
        double exchangeRate = getExchangeRate();
        String targetUUID = TypeChecker.trimUUID(String.valueOf(target.getUniqueId()));
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
                    if (Balances.getPlayerBalance(targetUUID) < exchangeRate * diamondAmount){
                        target.sendMessage(Component.translatable("balance.notenough", Styles.ERRORSTYLE));
                        return true;
                    }
                    Balances.executeTransaction("p2n", "bank", targetUUID, "Bank", exchangeRate * diamondAmount, "Bank withdrawal");
                    target.getInventory().addItem(new ItemStack(Material.DIAMOND, diamondAmount));
                    return true;
                case GOLD_INGOT:
                    // Deposit logic
                    target.sendMessage(Component.translatable("bank.inventory.deposit", Styles.INFOSTYLE));
                    return true;
                case GOLD_BLOCK:
                    // Check balance logic
                    target.closeInventory();
                    target.sendMessage(Component.translatable("balance.see", Component.text(Balances.getPlayerBalance(targetUUID))).style(Styles.INFOSTYLE));
                    return true;
                default:
                    // Do nothing for other item types
                    break;
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
        Balances.executeTransaction("n2p", "bank", "Bank", targetUUID, itemAmount * exchangeRate, "Bank deposit");
        return true;


    }

    /**
     * Returns the inventory instance for this bank GUI.
     * Required implementation of the {@link InventoryHolder} interface.
     * 
     * @return the bank inventory instance
     */
    @Override
    public Inventory getInventory() {
        return this.inventory;
    }

    /**
     * Retrieves the diamond-to-currency exchange rate from the plugin configuration.
     * This rate determines how much currency is equivalent to one diamond in banking operations.
     * 
     * <p>The method attempts to read the "exchangeRate" value from config.yml and validates
     * that it's a proper numeric value. If the configuration is missing or invalid,
     * it defaults to 10.0 and logs a warning.</p>
     * 
     * @return the exchange rate as a double value (defaults to 10.0 if not configured)
     */
    private static double getExchangeRate() {
        double exchangeRate = 10;
        if (plugin.getConfig().contains("exchangeRate") && plugin.getConfig().getString("exchangeRate") != null) {
            if (TypeChecker.isDouble(plugin.getConfig().getString("exchangeRate"))) exchangeRate = Double.parseDouble(plugin.getConfig().getString("exchangeRate"));
        } else {
            plugin.getLogger().warning("Could not find exchangeRate in config.yml!");
        }

        return exchangeRate;
    }
}
