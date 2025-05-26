package net.derfla.quickeconomy.util;

import net.derfla.quickeconomy.Main;
import net.derfla.quickeconomy.file.BalanceFile;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public class Balances {

    static Plugin plugin = Main.getInstance();
    static boolean SQLMode = Main.SQLMode;

    public static double getPlayerBalance(String uuid) {
        String trimmedUUID = TypeChecker.trimUUID(uuid);

        return AccountCache.getPlayerAccount(trimmedUUID).balance();
    }

    public static void setPlayerBalance(String uuid, double money) {
        String trimmedUUID = TypeChecker.trimUUID(uuid);

        AccountCache.getPlayerAccount(trimmedUUID).balance(money);

        if (SQLMode) {
            DatabaseManager.setPlayerBalance(uuid, money, 0).join();
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
        return  AccountCache.getPlayerAccount(trimmedUUID).change();
    }

    public static void setPlayerBalanceChange(String uuid, double moneyChange) {
        String trimmedUUID = TypeChecker.trimUUID(uuid);

        AccountCache.getPlayerAccount(trimmedUUID).change(moneyChange);

        if(SQLMode) {
            DatabaseManager.setPlayerBalanceChange(trimmedUUID, moneyChange).join();
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

        if(SQLMode) {
            return DatabaseManager.accountExists(trimmedUUID).join();
        }

        FileConfiguration file = BalanceFile.get();
        return file.contains("players." + trimmedUUID);
    }

    public static void executeTransaction(String transactType, String induce, String source,
                                          String destination, double amount, String transactionMessage) {
        String sourceUUID = TypeChecker.trimUUID(source);
        String destinationUUID = TypeChecker.trimUUID(destination);

        if(SQLMode) {
            DatabaseManager.executeTransaction(transactType, induce, source, destination, amount, transactionMessage).join();
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

        AccountCache.getPlayerAccount(uuid).name(name);

        if (SQLMode) {
            DatabaseManager.updatePlayerName(uuid, name).join();
            return;
        }
        FileConfiguration file = BalanceFile.get();

        if (file == null){
            plugin.getLogger().severe("balance.yml not found!");
            return;
        }
        file.set("players." + uuid + ".name", name);
        BalanceFile.save();
    }

    public static void addAccount(String uuid, String name) {

        AccountCache.addAccount(uuid, name);

        if (SQLMode) {
            DatabaseManager.addAccount(uuid, name, 0, 0,result -> {
                // Handle the callback if needed
            }).join();
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
