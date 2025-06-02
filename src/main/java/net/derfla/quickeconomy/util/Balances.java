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

public class Balances {

    static Plugin plugin = Main.getInstance();

    public static double getPlayerBalance(String uuid) {
        String trimmedUUID = TypeChecker.trimUUID(uuid);

        if (Main.SQLMode) {
            return AccountManagement.getPlayerBalance(trimmedUUID).join();
        }

        FileConfiguration file = BalanceFile.get();

        if (file == null){
            plugin.getLogger().severe("balance.yml not found!");
            return 0.0;
        }

        // Add safety check for account cache in file mode
        if (!AccountCache.accountExists(trimmedUUID)) {
            plugin.getLogger().warning("Account not found in cache for UUID: " + trimmedUUID + " in file mode, returning 0.0");
            return 0.0;
        }

        return AccountCache.getPlayerAccount(trimmedUUID).balance();
    }

    public static void setPlayerBalance(String uuid, double newBalance) {
        String trimmedUUID = TypeChecker.trimUUID(uuid);
        
        double currentBalance = getPlayerBalance(trimmedUUID);
        double balanceChange = newBalance - currentBalance;

        addPlayerBalanceChange(trimmedUUID, balanceChange);

        // Add safety check for account cache
        if (!AccountCache.accountExists(trimmedUUID)) {
            plugin.getLogger().warning("Account not found in cache for UUID: " + trimmedUUID + ", cannot set balance");
            return;
        }

        AccountCache.getPlayerAccount(trimmedUUID).balance(newBalance);

        if (Main.SQLMode) {
            AccountManagement.setPlayerBalance(uuid, newBalance, getPlayerBalanceChange(trimmedUUID)).join();
        } else {
            FileConfiguration file = BalanceFile.get();
            if (file == null){
                plugin.getLogger().warning("balance.yml not found!");
                return;
            }
            file.set("players." + trimmedUUID + ".balance", newBalance);
            BalanceFile.save();
        }
    }

    public static void addPlayerBalance(String uuid, double money){
        String trimmedUUID = TypeChecker.trimUUID(uuid);
        addPlayerBalanceChange(trimmedUUID, money);
        setPlayerBalance(trimmedUUID, getPlayerBalance(trimmedUUID) + money);
    }

    public static void subPlayerBalance(String uuid, double money){
        String trimmedUUID = TypeChecker.trimUUID(uuid);
        setPlayerBalance(trimmedUUID, getPlayerBalance(trimmedUUID) - money);
    }

    public static double getPlayerBalanceChange(String uuid) {
        String trimmedUUID = TypeChecker.trimUUID(uuid);
        
        // Add safety check for account cache
        if (!AccountCache.accountExists(trimmedUUID)) {
            plugin.getLogger().warning("Account not found in cache for UUID: " + trimmedUUID + ", returning 0.0 for balance change");
            return 0.0;
        }
        
        return AccountCache.getPlayerAccount(trimmedUUID).change();
    }

    public static void setPlayerBalanceChange(String uuid, double moneyChange) {
        String trimmedUUID = TypeChecker.trimUUID(uuid);

        // Add safety check for account cache
        if (!AccountCache.accountExists(trimmedUUID)) {
            plugin.getLogger().warning("Account not found in cache for UUID: " + trimmedUUID + ", cannot set balance change");
            return;
        }

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

    public static void addPlayerBalanceChange(String uuid, double money) {
        setPlayerBalanceChange(uuid, getPlayerBalanceChange(uuid) + money);
    }

    public static boolean hasAccount(String uuid) {
        String trimmedUUID = TypeChecker.trimUUID(uuid);

        if(AccountCache.accountExists(trimmedUUID)) return true;

        if(Main.SQLMode) {
            return (boolean) AccountManagement.accountExists(trimmedUUID).join();

        }

        FileConfiguration file = BalanceFile.get();
        return file.contains("players." + trimmedUUID);
    }

    public static void executeTransaction(String transactType, String induce, String source,
                                          String destination, double amount, String transactionMessage) {

        String sourceUUID = TypeChecker.trimUUID(source);
        String destinationUUID = TypeChecker.trimUUID(destination);

        if(Main.SQLMode) {
            TransactionManagement.executeTransaction(transactType, induce, source, destination, amount, transactionMessage).join();
            if (source != null) AccountCache.getPlayerAccount(sourceUUID).balance(AccountCache.getPlayerAccount(sourceUUID).balance() - amount);
            if (destination != null) AccountCache.getPlayerAccount(destinationUUID).balance(AccountCache.getPlayerAccount(destinationUUID).balance() + amount);
            return;
        }
        if (source != null)
            subPlayerBalance(source, amount);
        if (destination != null)
            addPlayerBalance(destination, amount);

    }

    public static void updatePlayerName(String uuid, String name) {

        // Add safety check for account cache
        if (!AccountCache.accountExists(uuid)) {
            plugin.getLogger().warning("Account not found in cache for UUID: " + uuid + ", cannot update player name");
            return;
        }

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
