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

/**
 * Utility class for managing an in-memory cache of player accounts to improve performance.
 * This cache reduces database/file system access by storing frequently accessed player account
 * data in memory for quick retrieval.
 * 
 * <p>The cache stores {@link PlayerAccount} objects mapped by their UUID (in trimmed format).
 * This allows for fast O(1) lookups when accessing player balance information, names, and
 * other account details.</p>
 * 
 * <p>The cache is initialized during plugin startup and populated from either the database
 * (in SQL mode) or the balance file (in file mode). The cache is kept synchronized with
 * the underlying storage throughout the plugin's operation.</p>
 * 
 * <p><strong>Thread Safety:</strong> This class is not thread-safe and should be accessed
 * from the main server thread only.</p>
 * 
 * @author QuickEconomy
 * @version 1.0
 * @since 1.0
 * @see PlayerAccount
 * @see DatabaseManager
 * @see BalanceFile
 */
public class AccountCache {

    static Plugin plugin = Main.getInstance();
    private static HashMap<String, PlayerAccount> accountMap;

    /**
     * Initializes the account cache by loading all player accounts from the configured storage system.
     * This method must be called during plugin startup (onEnable) to populate the cache.
     * 
     * <p>The initialization process varies depending on the plugin's mode:
     * <ul>
     *   <li><strong>SQL Mode:</strong> Loads accounts from the database using {@link DatabaseManager}</li>
     *   <li><strong>File Mode:</strong> Loads accounts from the balance.yml file</li>
     * </ul>
     * </p>
     * 
     * <p>After successful initialization, the total number of loaded accounts is logged.</p>
     * 
     * @throws RuntimeException if the cache cannot be initialized due to storage access errors
     */
    public static void init() {
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
     * Retrieves a PlayerAccount from the cache using the player's UUID.
     * This provides fast O(1) access to player account data without requiring database/file access.
     * 
     * @param UUID the player's UUID in trimmed format (32 characters without dashes)
     * @return the PlayerAccount object for the specified player, or null if not found in cache
     */
    public static PlayerAccount getPlayerAccount(String UUID) {
        return accountMap.get(UUID);
    }

    /**
     * Retrieves a list of all cached accounts as formatted strings for display purposes.
     * Each PlayerAccount is converted to its string representation using the custom toString method.
     * This method is primarily used for the '/bal list' command.
     * 
     * @return a list of formatted account strings, or null if the cache is empty
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
     * Adds a new account to the cache with default values (zero balance and change).
     * This method should only be used when creating a new player account, not during cache initialization.
     * For cache population during startup, use {@link #init()} instead.
     * 
     * @param uuid the UUID of the new player (in trimmed format)
     * @param name the name of the new player
     */
    public static void addAccount(String uuid, String name) {
        String timeStamp = TypeChecker.convertToUTC(Instant.now().atZone(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        accountMap.put(uuid, new PlayerAccount(name, 0, 0, timeStamp));
    }

    /**
     * Retrieves a player's UUID by searching the cache for their current name.
     * This performs a linear search through all cached accounts to find the matching name.
     * 
     * @param playerName the name of the player to search for
     * @return the player's UUID in trimmed format, or an empty string if not found
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

    /**
     * Checks if an account exists in the cache for the specified UUID.
     * This provides a fast way to verify account existence without accessing underlying storage.
     * 
     * @param uuid the player's UUID in trimmed format
     * @return true if the account exists in the cache, false otherwise
     */
    public static boolean accountExists(String uuid) {
        return accountMap.containsKey(uuid);
    }
    
    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private AccountCache() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}
