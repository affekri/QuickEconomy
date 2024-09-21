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
            DatabaseManager.setPlayerBalance(uuid, money);
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
        if (Bukkit.getPlayer(UUID.fromString(TypeChecker.untrimUUID(uuid))) == null){
            addPlayerBalanceChange(trimmedUUID, money);
        }
        setPlayerBalance(trimmedUUID, getPlayerBalance(trimmedUUID) + money);
    }

    public static void subPlayerBalance(String uuid, float money){
        String trimmedUUID = TypeChecker.trimUUID(uuid);
        setPlayerBalance(trimmedUUID, getPlayerBalance(trimmedUUID) - money);
    }

    public static float getPlayerBalanceChange(String uuid) {
        FileConfiguration file = BalanceFile.get();

        if (file == null){
            plugin.getLogger().warning("balance.yml not found!");
            return 0.0f;
        }
        if (!(file.contains("players." + uuid + ".change"))) {
            return 0.0f;
        }

        int playerBalanceChange = file.getInt("players." + uuid + ".change");
        float fMoney = (float) playerBalanceChange;
        return fMoney / 100;
    }
    public static void setPlayerBalanceChange(String uuid, float money) {
        FileConfiguration file = BalanceFile.get();

        if (file == null){
            plugin.getLogger().warning("balance.yml not found!");
            return;
        }

        float multMoney = money * 100;
        int intMoney = (int) multMoney;
        file.set("players." + uuid + ".change", intMoney);
        BalanceFile.save();
    }

    public static void addPlayerBalanceChange(String uuid, float money) {
        setPlayerBalanceChange(uuid, getPlayerBalanceChange(uuid) + money);
    }

    public static boolean hasAccount(String uuid) {
        String trimmedUUID = TypeChecker.trimUUID(uuid);
        if(SQLMode)
            return DatabaseManager.accountExists(trimmedUUID);

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
}
