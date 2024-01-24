package net.derfla.quickeconomy.utils;


import net.derfla.quickeconomy.files.balanceFile;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

public class balances {

    public static float getPlayerBalance(String playerName) {
        FileConfiguration file = balanceFile.get();

        if (file == null){
            Bukkit.getLogger().warning("balance.yml not found!");
            return 0.0f;
        }

        if (!(file.contains("players." + playerName + ".balance"))) {
            Bukkit.getLogger().info("No balance found for player: " + playerName);
            return 0.0f;
        }

        int playerBalance = file.getInt("players." + playerName + ".balance");
        float fMoney = (float) playerBalance;
        return fMoney / 100;
    }

    public static void setPlayerBalance(String playerName, float money) {
        FileConfiguration file = balanceFile.get();

        if (file == null){
            Bukkit.getLogger().warning("balance.yml not found!");
            return;
        }

        float multMoney = money * 100;
        int intMoney = (int) multMoney;
        file.set("players." + playerName + ".balance", intMoney);
        Bukkit.getLogger().info("Set balance of " + playerName + " to " + money);
        balanceFile.save();
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
        FileConfiguration file = balanceFile.get();

        if (file == null){
            Bukkit.getLogger().warning("balance.yml not found!");
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
        FileConfiguration file = balanceFile.get();

        if (file == null){
            Bukkit.getLogger().warning("balance.yml not found!");
            return;
        }

        float multMoney = money * 100;
        int intMoney = (int) multMoney;
        file.set("players." + playerName + ".change", intMoney);
        balanceFile.save();
    }

    public static void addPlayerBalanceChange(String playerName, float money) {
        setPlayerBalanceChange(playerName, getPlayerBalanceChange(playerName) + money);
    }
}

