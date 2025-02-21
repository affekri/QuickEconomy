package net.derfla.quickeconomy.util;

import net.derfla.quickeconomy.Main;
import net.derfla.quickeconomy.file.BalanceFile;
import net.derfla.quickeconomy.model.PlayerAccount;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class AccountCache {

    static Plugin plugin = Main.getInstance();
    private static HashMap<String, PlayerAccount> accountMap;

    /**
     * Method to call to initiate the account cache.
     * Is called in startup, in onEnable.
     */
    public static void init() {
        // For loop. for every line in playerAccounts table/player in balance.yml. Create a new entry into the hashmap, with key Player UUID

        if(Main.SQLMode) {
            accountMap = DatabaseManager.listAllAccounts().join();
        } else {
            FileConfiguration file = BalanceFile.get();
            ConfigurationSection players = file.getConfigurationSection("players");
            for(String uuid : players.getKeys(false)){
                accountMap.put(uuid,
                        new PlayerAccount(players.getString(uuid + ".name"), players.getDouble(uuid + ".balance"), players.getDouble(uuid + ".change"), players.getString(uuid + ".created")));
            }
        }
        plugin.getLogger().info(accountMap.size() + " accounts from database now stored in cache.");

    }

    /**
     * Get a PlayerAccount from the AccountCache.
     * @param UUID The UUID of the player to get the PlayerAccount object.
     * @return PlayerAccount of the corresponding player.
     */
    public static PlayerAccount getPlayerAccount(String UUID) {
        return accountMap.get(UUID);
    }

    /**
     * Get a list of strings. Each PlayerAccount has their own string in the list. Utilizes the custom PlayerAccount.toString method. Mainly built for the '/bal list' command.
     * @return A list of PlayerAccount strings.
     */
    public static List<String>listAllAccounts() {
        List<String> accountList = new ArrayList<>();
        if(accountMap.isEmpty()) {
            plugin.getLogger().warning("No accounts found in player cache.");
            return null;
        }
        for(String uuid : accountMap.keySet()) {
            accountList.add(accountMap.get(uuid).toString());
        }
        return accountList;
    }

    /**
     * Add an account to the cache. This should only be used when a new account is actually being created. Not when just populating the cache, for this refer to AccountCache.init().
     * @param uuid The UUID of the new player.
     * @param name The name of the new player.
     */
    public static void addAccount(String uuid, String name) {
        String timeStamp = TypeChecker.convertToUTC(Instant.now().atZone(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        accountMap.put(uuid, new PlayerAccount(name, 0, 0, timeStamp));
    }

    /**
     * Get the UUID from a player in the account cache.
     * @param playerName The name of the player.
     * @return A string with the UUID. If the playerName is not found in the cache, it returns an empty string.
     */
    public static String getUUID(String playerName) {
        for(String uuid : accountMap.keySet()) {
            if (playerName.equals(accountMap.get(uuid).name())) {
                return uuid;
            }
        }
        plugin.getLogger().warning("Failed to get UUID for player: " + playerName);
        return "";
    }

}
