package net.derfla.quickeconomy.util;

import net.derfla.quickeconomy.Main;
import net.derfla.quickeconomy.file.BalanceFile;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

public class Balances {

    static Plugin plugin = Main.getInstance();

    public static float getPlayerBalance(String uuid) {
        String trimmedUUID = TypeChecker.trimUUID(uuid);
        if (Main.SQLMode) {
            double balance = DatabaseManager.displayBalance(trimmedUUID).join();
            return (float) balance;
        }

        FileConfiguration file = BalanceFile.get();

        if (file == null){
            plugin.getLogger().severe("balance.yml not found!");
            return 0.0f;
        }

        if (!(file.contains("players." + trimmedUUID + ".balance"))) {
            plugin.getLogger().info("No balance found for player: " + MojangAPI.getName(trimmedUUID).join());
            return 0.0f;
        }
        if (file.get("players." + trimmedUUID + ".balance") == null) {
            plugin.getLogger().info("No balance found for player: " + MojangAPI.getName(trimmedUUID).join());
            return 0.0f;
        }
        return (float) file.getDouble("players." + trimmedUUID + ".balance");
    }

    public static void setPlayerBalance(String uuid, float money) {
        String trimmedUUID = TypeChecker.trimUUID(uuid);
        if (Main.SQLMode) {
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
        String trimmedUUID = TypeChecker.trimUUID(uuid);
        if(Main.SQLMode) {
            double change = DatabaseManager.getPlayerBalanceChange(trimmedUUID).join();
            return (float) change;
        }

        FileConfiguration file = BalanceFile.get();

        if (file == null){
            plugin.getLogger().warning("balance.yml not found!");
            return 0.0f;
        }
        if (!(file.contains("players." + trimmedUUID + ".change"))) {
            return 0.0f;
        }

        return (float) file.getDouble("players." + trimmedUUID + ".change");
    }

    public static void setPlayerBalanceChange(String uuid, float moneyChange) {
        String trimmedUUID = TypeChecker.trimUUID(uuid);
        if(Main.SQLMode) {
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

    public static void addPlayerBalanceChange(String uuid, float money) {
        setPlayerBalanceChange(uuid, getPlayerBalanceChange(uuid) + money);
    }

    public static boolean hasAccount(String uuid) {
        String trimmedUUID = TypeChecker.trimUUID(uuid);
        if(Main.SQLMode) {
            return (boolean) DatabaseManager.accountExists(trimmedUUID).join();
        }

        FileConfiguration file = BalanceFile.get();
        return file.contains("players." + trimmedUUID);
    }

    public static void executeTransaction(String transactType, String induce, String source,
                                          String destination, double amount, String transactionMessage) {
        if(Main.SQLMode) {
            DatabaseManager.executeTransaction(transactType, induce, source, destination, amount, transactionMessage).join();
            return;
        }
        if (source != null)
            subPlayerBalance(source, (float) amount);
        if (destination != null)
            addPlayerBalance(destination, (float) amount);

    }

    public static void updatePlayerName(String uuid, String name) {
        if (Main.SQLMode) {
            DatabaseManager.updatePlayerName(uuid, name).join();
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
        if (Main.SQLMode) {
            DatabaseManager.addAccount(uuid, name, 0.0, 0.0, result -> {}).join();
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

    public static String getUUID(String playerName) {
        if (Main.SQLMode) {
            return DatabaseManager.getUUID(playerName).join();
        }
        FileConfiguration file = BalanceFile.get();

        if (file == null){
            plugin.getLogger().severe("balance.yml not found!");
            return "";
        }
        ConfigurationSection players = file.getConfigurationSection("players");
        List<String> keys = new ArrayList<>(players.getKeys(false));
        int totalKeys = keys.size();
        for(int i = 0; i < totalKeys;i++) {
            String key = keys.get(i);
            if(players.getString(key + ".name").toLowerCase().equals(playerName.toLowerCase())) {
                return key;
            }
        }
        plugin.getLogger().warning("Failed to get UUID for player: " + playerName);
        return "";
    }
}
