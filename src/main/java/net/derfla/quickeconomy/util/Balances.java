package net.derfla.quickeconomy.util;


import net.derfla.quickeconomy.file.BalanceFile;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

public class Balances {

    static Plugin plugin = Bukkit.getPluginManager().getPlugin("QuickEconomy");

    public static float getPlayerBalance(String playerName) {
        FileConfiguration file = BalanceFile.get();

        if (file == null){
            plugin.getLogger().severe("balance.yml not found!");
            return 0.0f;
        }

        if (!(file.contains("players." + playerName + ".balance"))) {
            plugin.getLogger().info("No balance found for player: " + playerName);
            return 0.0f;
        }
        if (file.get("players." + playerName + ".balance") == null) {
            plugin.getLogger().info("No balance found for player: " + playerName);
            return 0.0f;
        }
        return (float) file.getDouble("players." + playerName + ".balance");
    }

    public static void setPlayerBalance(String playerName, float money) {
        FileConfiguration file = BalanceFile.get();

        if (file == null){
            plugin.getLogger().warning("balance.yml not found!");
            return;
        }
        file.set("players." + playerName + ".balance", money);
        plugin.getLogger().info("Set balance of " + playerName + " to " + money);
        BalanceFile.save();
    }

    public static void addPlayerBalance(String playerName, float money){
        if (Bukkit.getPlayer(playerName) == null){
            addPlayerBalanceChange(playerName, money);
        }
        setPlayerBalance(playerName, getPlayerBalance(playerName) + money);
    }

    public static void subPlayerBalance(String playerName, float money){
        setPlayerBalance(playerName, getPlayerBalance(playerName) - money);
    }

    public static float getPlayerBalanceChange(String playerName) {
        FileConfiguration file = BalanceFile.get();

        if (file == null){
            plugin.getLogger().warning("balance.yml not found!");
            return 0.0f;
        }
        if (!(file.contains("players." + playerName + ".change"))) {
            return 0.0f;
        }

        int playerBalanceChange = file.getInt("players." + playerName + ".change");
        float fMoney = (float) playerBalanceChange;
        return fMoney / 100;
    }
    public static void setPlayerBalanceChange(String playerName, float money) {
        FileConfiguration file = BalanceFile.get();

        if (file == null){
            plugin.getLogger().warning("balance.yml not found!");
            return;
        }

        float multMoney = money * 100;
        int intMoney = (int) multMoney;
        file.set("players." + playerName + ".change", intMoney);
        BalanceFile.save();
    }

    public static void addPlayerBalanceChange(String playerName, float money) {
        setPlayerBalanceChange(playerName, getPlayerBalanceChange(playerName) + money);
    }
}

