package net.derfla.quickeconomy.util;


import net.derfla.quickeconomy.Main;
import net.derfla.quickeconomy.file.BalanceFile;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

public class Balances {

    static Plugin plugin = Main.getInstance();
    static boolean SQLMode = Main.SQLMode;

    public static float getPlayerBalance(String uuid) {
        String trimmedUUID = TypeChecker.trimUUID(uuid);
        if (SQLMode) {
            return (float) DatabaseManager.displayBalance(trimmedUUID);
        }

        FileConfiguration file = BalanceFile.get();

        if (file == null){
            plugin.getLogger().severe("balance.yml not found!");
            return 0.0f;
        }

        if (!(file.contains("players." + trimmedUUID + ".balance"))) {
            plugin.getLogger().info("No balance found for player: " + MojangAPI.getName(trimmedUUID));
            return 0.0f;
        }
        if (file.get("players." + trimmedUUID + ".balance") == null) {
            plugin.getLogger().info("No balance found for player: " + MojangAPI.getName(trimmedUUID));
            return 0.0f;
        }
        return (float) file.getDouble("players." + trimmedUUID + ".balance");
    }

    public static void setPlayerBalance(String uuid, float money) {
        String trimmedUUID = TypeChecker.trimUUID(uuid);
        if (SQLMode) {
            DatabaseManager.setPlayerBalance(uuid, money, 0);
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

    public static void addPlayerBalance(String uuid, float money){
        String trimmedUUID = TypeChecker.trimUUID(uuid);
        addPlayerBalanceChange(trimmedUUID, money);
        setPlayerBalance(trimmedUUID, getPlayerBalance(trimmedUUID) + money);
    }

    public static void subPlayerBalance(String uuid, float money){
        String trimmedUUID = TypeChecker.trimUUID(uuid);
        setPlayerBalance(trimmedUUID, getPlayerBalance(trimmedUUID) - money);
    }

    public static float getPlayerBalanceChange(String uuid) {
        if(SQLMode)
            return (float) DatabaseManager.getPlayerBalanceChange(uuid);

        FileConfiguration file = BalanceFile.get();

        if (file == null){
            plugin.getLogger().warning("balance.yml not found!");
            return 0.0f;
        }
        if (!(file.contains("players." + uuid + ".change"))) {
            return 0.0f;
        }

        return (float) file.getDouble("players." + uuid + ".change");
    }
    public static void setPlayerBalanceChange(String uuid, float moneyChange) {
        if(SQLMode) {
            DatabaseManager.setPlayerBalanceChange(uuid, moneyChange);
            return;
        }

        FileConfiguration file = BalanceFile.get();
        String trimmedUUID = TypeChecker.trimUUID(uuid);

        if (file == null){
            plugin.getLogger().warning("balance.yml not found!");
            return;
        }

        file.set("players." + trimmedUUID + ".change", moneyChange);
        BalanceFile.save();
    }

    public static void addPlayerBalanceChange(String uuid, float money) {
        setPlayerBalanceChange(uuid, getPlayerBalanceChange(uuid) + money);
    }

    public static boolean hasAccount(String uuid) {
        String trimmedUUID = TypeChecker.trimUUID(uuid);
        if(SQLMode) {
            return DatabaseManager.accountExists(trimmedUUID);
        }

        FileConfiguration file = BalanceFile.get();
        return file.contains("players." + trimmedUUID);
    }

    public static void executeTransaction(String transactType, String induce, String source,
                                          String destination, double amount, String transactionMessage) {
        if(SQLMode) {
            DatabaseManager.executeTransaction(transactType, induce, source, destination, amount, transactionMessage);
            return;
        }
        if (source != null)
            subPlayerBalance(source, (float) amount);
        if (destination != null)
            addPlayerBalance(destination, (float) amount);

    }

    public static void updatePlayerName(String uuid, String name) {
        if (SQLMode) {
            DatabaseManager.updatePlayerName(uuid, name);
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
        if (SQLMode) {
            DatabaseManager.addAccount(uuid, name, 0, 0);
            return;
        }
        FileConfiguration file = BalanceFile.get();

        if (file == null){
            plugin.getLogger().severe("balance.yml not found!");
            return;
        }
        file.set("players." + uuid + ".name", name);
        file.set("players." + uuid + ".balance", 0.0f);
        file.set("players." + uuid + ".change", 0.0f);
        BalanceFile.save();
    }
}
