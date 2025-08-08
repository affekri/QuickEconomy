package net.derfla.quickeconomy.util;

import net.derfla.quickeconomy.Main;
import net.derfla.quickeconomy.database.AccountManagement;
import net.derfla.quickeconomy.database.TransactionManagement;
import net.derfla.quickeconomy.file.BalanceFile;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Player balance utilities for QuickEconomy.
 * <p>
 * Provides read and write operations for balances and balance change snapshots,
 * transparently supporting both SQL mode and file mode based on {@link net.derfla.quickeconomy.Main#SQLMode}.
 * This class also exposes simple helpers for account presence checks and performing
 * high-level transactions that delegate to database logic when enabled.
 */
public class Balances {

    static Plugin plugin = Main.getInstance();

    /**
     * Get the current balance for a player.
     *
     * @param uuid player UUID (trimmed or dashed)
     * @return current balance; 0.0 when not found or file unavailable
     */
    public static double getPlayerBalance(String uuid) {
        String trimmedUUID = TypeChecker.trimUUID(uuid);

        if (Main.SQLMode) {
            double balance = AccountManagement.displayBalance(trimmedUUID).join();
            return balance;
        }

        FileConfiguration file = BalanceFile.get();

        if (file == null){
            plugin.getLogger().severe("balance.yml not found!");
            return 0.0;
        }


        return AccountCache.getPlayerAccount(trimmedUUID).balance();
    }

    /**
     * Set the player's balance.
     * Updates the in-memory cache and then persists to SQL or balance.yml.
     *
     * @param uuid  player UUID (trimmed or dashed)
     * @param money new balance amount
     */
    public static void setPlayerBalance(String uuid, double money) {
        String trimmedUUID = TypeChecker.trimUUID(uuid);

        AccountCache.getPlayerAccount(trimmedUUID).balance(money);
        if (Main.SQLMode) {
            AccountManagement.setPlayerBalance(uuid, money, 0).join();
            return;
        }

        FileConfiguration file = BalanceFile.get();
        if (file == null){
            plugin.getLogger().warning("balance.yml not found!");
            return;
        }
        file.set("players." + trimmedUUID + ".balance", money);
        BalanceFile.save();
    }

    /**
     * Increase the player's balance by the given amount and record balance change.
     *
     * @param uuid  player UUID (trimmed or dashed)
     * @param money positive amount to add
     */
    public static void addPlayerBalance(String uuid, double money){
        String trimmedUUID = TypeChecker.trimUUID(uuid);
        if (uuid != null) addPlayerBalanceChange(trimmedUUID, money);
        setPlayerBalance(trimmedUUID, getPlayerBalance(trimmedUUID) + money);
    }

    /**
     * Decrease the player's balance by the given amount.
     *
     * @param uuid  player UUID (trimmed or dashed)
     * @param money positive amount to subtract
     */
    public static void subPlayerBalance(String uuid, double money){
        String trimmedUUID = TypeChecker.trimUUID(uuid);
        setPlayerBalance(trimmedUUID, getPlayerBalance(trimmedUUID) - money);
    }

    /**
     * Get the current balance change accumulator.
     *
     * @param uuid player UUID (trimmed or dashed)
     * @return change value in memory (persisted depending on mode)
     */
    public static double getPlayerBalanceChange(String uuid) {
        String trimmedUUID = TypeChecker.trimUUID(uuid);
        return  AccountCache.getPlayerAccount(trimmedUUID).change();
    }

    /**
     * Set the current balance change accumulator.
     *
     * @param uuid         player UUID (trimmed or dashed)
     * @param moneyChange  new change value
     */
    public static void setPlayerBalanceChange(String uuid, double moneyChange) {
        String trimmedUUID = TypeChecker.trimUUID(uuid);

        AccountCache.getPlayerAccount(trimmedUUID).change(moneyChange);

        if(Main.SQLMode) {

            AccountManagement.setPlayerBalanceChange(trimmedUUID, moneyChange).join();
            return;
        }

        FileConfiguration file = BalanceFile.get();

        if (file == null){
            plugin.getLogger().warning("balance.yml not found!");
            return;
        }

        file.set("players." + trimmedUUID + ".change", moneyChange);
        BalanceFile.save();
    }

    /**
     * Increment the balance change accumulator by the given amount.
     *
     * @param uuid  player UUID (trimmed or dashed)
     * @param money delta to add to change accumulator
     */
    public static void addPlayerBalanceChange(String uuid, double money) {
        setPlayerBalanceChange(uuid, getPlayerBalanceChange(uuid) + money);
    }

    /**
     * Check whether an account exists for the given UUID in the cache.
     *
     * @param uuid player UUID (trimmed or dashed)
     * @return true if present in cache; false otherwise
     */
    public static boolean hasAccountUUID(String uuid) {
        String trimmedUUID = TypeChecker.trimUUID(uuid);

        return AccountCache.accountExistsUUID(trimmedUUID);
    }

    /**
     * Check whether an account exists for the given player name in the cache.
     *
     * @param playerName player name to search for
     * @return true if present in cache; false otherwise
     */
    public static boolean hasAccountName(String playerName) {
        return AccountCache.accountExistsName(playerName);
    }

    /**
     * Creates a transaction between two players or one player and a logical 'null' account.
     * Delegates to database-backed logic in SQL mode; otherwise adjusts balances in file mode.
     *
     * @param transactType       transaction kind: 'p2p', 'p2n', or 'n2p'
     * @param induce             what triggered the transaction (e.g., command)
     * @param source             source UUID for 'p2x', or logical source for 'n2p'
     * @param destination        destination UUID for 'x2p', or logical destination for 'p2n'
     * @param amount             amount to transfer (positive)
     * @param transactionMessage optional description
     * @throws IllegalArgumentException when parameters are invalid for the transaction type
     */
    public static void executeTransaction(String transactType, String induce, String source,
                                          String destination, double amount, String transactionMessage) {
        if (!("p2p".equalsIgnoreCase(transactType) || "p2n".equalsIgnoreCase(transactType) || "n2p".equalsIgnoreCase(transactType))) {
            throw new IllegalArgumentException("Invalid transaction type! Allowed values are 'p2p', 'p2n' and 'n2p'");
        }
        if (transactType.charAt(0) == 'p' && source.length() != 32) {
            throw new IllegalArgumentException("This transaction type requires the source parameter to be a trimmed player UUID!");
        }
        if (transactType.charAt(2) == 'p' && destination.length() != 32) {
            throw new IllegalArgumentException("This transaction type requires the destination parameter to be a trimmed player UUID!");
        }

        if(Main.SQLMode) {
            // Execute the transaction for SQL mode
            TransactionManagement.executeTransaction(transactType.toLowerCase(), induce, source, destination, amount, transactionMessage).join();

            // Update the account cache for SQL mode. For file mode account cache will be updated in setPlayerBalance.
            if (transactType.charAt(0) == 'p') AccountCache.getPlayerAccount(source).balance(AccountCache.getPlayerAccount(source).balance() - amount);
            if (transactType.charAt(2) == 'p') AccountCache.getPlayerAccount(destination).balance(AccountCache.getPlayerAccount(destination).balance() + amount);
            return;
        }
        if (transactType.charAt(0) == 'p')
            subPlayerBalance(source, amount);
        if (transactType.charAt(2) == 'p')
            addPlayerBalance(destination, amount);

    }

    /**
     * Update the player name associated with the given UUID.
     * Updates cache first, then persists to SQL or file if changed.
     *
     * @param uuid player UUID (trimmed or dashed)
     * @param name new player name
     */
    public static void updatePlayerName(String uuid, String name) {


        AccountCache.getPlayerAccount(uuid).name(name);

        if (Main.SQLMode) {

            AccountManagement.updatePlayerName(uuid, name).join();
            return;
        }
        FileConfiguration file = BalanceFile.get();

        if (file == null){
            plugin.getLogger().severe("balance.yml not found!");
            return;
        }
        
        // Check if the current name is different before updating
        String currentName = file.getString("players." + uuid + ".name");
        if (currentName != null && currentName.equals(name)) {
            // Name hasn't changed, no need to update
            return;
        }
        
        // Name has changed or is new, proceed with update
        file.set("players." + uuid + ".name", name);
        BalanceFile.save();
        
        // Log only if there was an actual name change (not initial setting)
        if (currentName != null) {
            plugin.getLogger().info("Updated player name for UUID " + TypeChecker.untrimUUID(uuid) + ": " + currentName + " -> " + name);
        }
    }

    /**
     * Create a new account with zero balance and change values.
     * Updates cache first, then persists to SQL or balance.yml.
     *
     * @param uuid player UUID (trimmed or dashed)
     * @param name player name
     */
    public static void addAccount(String uuid, String name) {

        AccountCache.addAccount(uuid, name);

        if (Main.SQLMode) {
            AccountManagement.addAccount(uuid, name, 0.0, 0.0, result -> {}).join();
            return;
        }
        FileConfiguration file = BalanceFile.get();

        if (file == null){
            plugin.getLogger().severe("balance.yml not found!");
            return;
        }
        String timeStamp = TypeChecker.convertToUTC(Instant.now().atZone(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        file.set("players." + uuid + ".name", name);
        file.set("players." + uuid + ".balance", 0.0);
        file.set("players." + uuid + ".change", 0.0);
        file.set("players." + uuid + ".created", timeStamp);
        BalanceFile.save();
    }

    /**
     * Get the UUID from a player
     * @deprecated
     * Since 1.1.2. Use AccountCache.getUUID() instead.
     *
     * @param playerName The name of the player.
     * @return A string with the UUID. If the playerName is not found in the cache, it returns an empty string.
     */
    @Deprecated
    public static String getUUID(String playerName) {
        return AccountCache.getUUID(playerName);
    }
}
