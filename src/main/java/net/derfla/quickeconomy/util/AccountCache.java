package net.derfla.quickeconomy.util;

import net.derfla.quickeconomy.Main;
import net.derfla.quickeconomy.database.AccountManagement;
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
import java.util.Map;

public class AccountCache {

    static Plugin plugin = Main.getInstance();
    private static HashMap<String, PlayerAccount> accountMap;

    /**
     * Method to call to initiate the account cache.
     * Is called in startup, in onEnable.
     */
    public static void init() {
        if(Main.SQLMode) {
            accountMap = AccountManagement.listAllAccounts().join();
        } else {
            accountMap = new HashMap<>();
            FileConfiguration file = BalanceFile.get();
            if (file == null) {
                plugin.getLogger().severe("BalanceFile is null during AccountCache.init() in file mode. Cannot load accounts.");
                return;
            }
            ConfigurationSection players = file.getConfigurationSection("players");
            if (players == null) {
                plugin.getLogger().info("No 'players' section in BalanceFile during AccountCache.init(). No accounts loaded.");
                return;
            }
            for(String uuid : players.getKeys(false)){
                accountMap.put(uuid,
                        new PlayerAccount(players.getString(uuid + ".name"), players.getDouble(uuid + ".balance"), players.getDouble(uuid + ".change"), players.getString(uuid + ".created")));
            }
        }
        if (accountMap != null) {
            plugin.getLogger().info(accountMap.size() + " accounts from " + (Main.SQLMode ? "database" : "file") + " now stored in cache.");
        } else {
            plugin.getLogger().warning("AccountCache accountMap is null after initialization attempt.");
        }
    }

    /**
     * Get a PlayerAccount from the AccountCache.
     * @param UUID The UUID of the player to get the PlayerAccount object.
     * @return PlayerAccount of the corresponding player, or null if not found.
     */
    public static PlayerAccount getPlayerAccount(String UUID) {
        if (accountMap == null) return null;
        return accountMap.get(TypeChecker.trimUUID(UUID));
    }

    /**
     * Get a list of strings. Each PlayerAccount has their own string in the list. Utilizes the custom PlayerAccount.toString method. Mainly built for the '/bal list' command.
     * @return A list of PlayerAccount strings.
     */
    public static List<String>listAllAccounts() {
        List<String> accountList = new ArrayList<>();
        if(accountMap == null || accountMap.isEmpty()) {
            plugin.getLogger().warning("No accounts found in player cache.");
            return accountList;
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
        if (accountMap == null) accountMap = new HashMap<>();
        String timeStamp = TypeChecker.convertToUTC(Instant.now().atZone(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        accountMap.put(TypeChecker.trimUUID(uuid), new PlayerAccount(name, 0, 0, timeStamp));
    }

    /**
     * Get the UUID from a player in the account cache.
     * @param playerName The name of the player.
     * @return A string with the UUID. If the playerName is not found in the cache, it returns an empty string.
     */
    public static String getUUID(String playerName) {
        if (accountMap == null || playerName == null || playerName.isEmpty()) return "";
        for(Map.Entry<String, PlayerAccount> entry : accountMap.entrySet()) {
            if (playerName.equals(entry.getValue().name())) {
                return entry.getKey();
            }
        }
        plugin.getLogger().warning("Failed to get UUID for player: " + playerName + " from cache.");
        return "";
    }

    /**
     * Checks if the provided UUID key exists in the account cache.
     * @param uuid The UUID of the player.
     * @return True if the UUID is present in the cache. False if it's not.
     */
    public static boolean accountExists(String uuid) {
        if (accountMap == null || uuid == null || uuid.isEmpty()) return false;
        return accountMap.containsKey(TypeChecker.trimUUID(uuid));
    }

    /**
     * Clears the BalChange (offline gains) for all accounts currently held in the cache.
     * This is typically called after a database-wide rollback that resets BalChange in the DB.
     */
    public static void clearAllCachedBalanceChanges() {
        if (accountMap == null || accountMap.isEmpty()) {
            plugin.getLogger().info("[AccountCache] No accounts in cache to clear BalChange for.");
            return;
        }
        int count = 0;
        for (PlayerAccount account : accountMap.values()) {
            if (account != null) {
                account.change(0.0);
                count++;
            }
        }
        plugin.getLogger().info("[AccountCache] Cleared BalChange for " + count + " accounts in cache.");
    }
}
