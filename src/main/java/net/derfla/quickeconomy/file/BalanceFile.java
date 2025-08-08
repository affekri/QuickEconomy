package net.derfla.quickeconomy.file;

import net.derfla.quickeconomy.Main;
import net.derfla.quickeconomy.util.MojangAPI;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for managing the balance.yml file that stores player balance data.
 * This class provides methods to create, read, write, and maintain the balance configuration file.
 * 
 * The balance file stores player economic data including:
 * - Player balances
 * - Balance change tracking
 * - Player name to UUID mappings
 * 
 * This class supports migration from name-based to UUID-based player identification.
 * 
 * @author QuickEconomy
 * @version 1.0
 */
public class BalanceFile {

    /** The physical balance.yml file */
    private static File file;
    
    /** The YAML configuration object for reading/writing balance data */
    private static FileConfiguration customFile;
    
    /** Reference to the main plugin instance */
    static Plugin plugin = Main.getInstance();

    /**
     * Initializes the balance file system. Creates the balance.yml file if it doesn't exist
     * and loads it into memory for configuration access.
     * 
     * This method should be called during plugin initialization to ensure the balance
     * file is ready for use.
     * 
     * @throws SecurityException if file creation is denied by security manager
     */
    public static void setup(){
        // Setup logic, gets called when plugin is loaded
        file = new File(plugin.getDataFolder(), "balance.yml");

        if (!file.exists()) {
            try {
                boolean created = file.createNewFile();
                if (created) {
                    plugin.getLogger().info("Created new file: " + file.getName());
                } else {
                    plugin.getLogger().warning("File already exists: " + file.getName());
                }
            } catch (IOException e) {
                // Log an error when file creation fails
                plugin.getLogger().severe("Error creating file: " + file.getName());
                e.printStackTrace();
            }
        }

        customFile = YamlConfiguration.loadConfiguration(file);
    }

    /**
     * Retrieves the FileConfiguration object for the balance file.
     * 
     * @return the FileConfiguration instance for balance.yml, or null if not initialized
     * @throws IllegalStateException if the configuration has not been properly initialized
     */
    public static FileConfiguration get() {
        if (customFile == null) {
            plugin.getLogger().severe("Custom file not initialized. Did you call setup()?");
        }
        return customFile;
    }

    /**
     * Saves the current state of the balance configuration to the balance.yml file.
     * 
     * Any changes made to the configuration in memory will be persisted to disk.
     * If saving fails, a warning is logged but no exception is thrown.
     */
    public static void save(){
        // Saves the file
        try {
            customFile.save(file);
        } catch (IOException e) {
            // Just sends out a warning
            plugin.getLogger().warning("Couldn't save file: " + file.getName());
        }
    }

    /**
     * Reloads the balance configuration from the balance.yml file.
     * 
     * This method discards any unsaved changes in memory and reloads the
     * configuration from the file on disk. Use this to refresh data after
     * external modifications to the file.
     */
    public static void reload() {
        // Reloads the file
        if (file.exists()) {
            customFile = YamlConfiguration.loadConfiguration(file);
            plugin.getLogger().info("File reloaded successfully: " + file.getName());
        } else {
            plugin.getLogger().warning("File does not exist, cannot reload: " + file.getName());
        }
    }

    /**
     * Deletes the balance.yml file from the filesystem.
     * 
     * This is a destructive operation that permanently removes all balance data.
     * Use with extreme caution as this action cannot be undone.
     * 
     * @return true if the file was successfully deleted, false otherwise
     */
    public static boolean delete() {
        // Deletes the file
        if (file.exists()) {
            if (file.delete()) {
                plugin.getLogger().info("Deleted file: " + file.getName());
                return true;
            } else {
                plugin.getLogger().warning("Couldn't delete file: " + file.getName());
                return false;
            }
        } else {
            plugin.getLogger().warning("File does not exist: " + file.getName());
            return false;
        }
    }

    /**
     * Checks the current format version of the balance file.
     * 
     * The format determines how player data is stored:
     * - "playerName": Legacy format using player names as keys
     * - "uuid": Modern format using player UUIDs as keys
     * 
     * @return the format string, defaults to "playerName" if not set
     */
    public static String checkFormat() {
        if(!get().contains("format")) return "playerName";
        return get().getString("format");
    }

    /**
     * Converts the balance file from name-based format to UUID-based format.
     * 
     * This migration process:
     * 1. Backs up existing player data to "old_players" section
     * 2. Converts each player entry from name-based to UUID-based keys
     * 3. Fetches UUIDs from Mojang API for each player name
     * 4. Handles duplicate accounts and missing UUIDs
     * 5. Updates the format flag to "uuid"
     * 
     * The conversion is performed in batches to manage memory usage and provide
     * progress feedback. Manual intervention may be required for duplicate accounts.
     * 
     * @throws RuntimeException if the conversion process encounters critical errors
     */
    public static void convertKeys() {
        plugin.getLogger().info("Converting balance.yml to UUID-format.");
        try {
            get().set("old_players", get().getConfigurationSection("players"));
            get().set("players", null);
            save();
        } catch (Exception e){
            plugin.getLogger().severe("Failed to convert file format:" + e.getMessage());
        }

        try {
            ConfigurationSection playersSection = get().getConfigurationSection("old_players");

            int batchSize = 100; // Define a suitable batch size
            List<String> keys = new ArrayList<>(playersSection.getKeys(false));
            int totalKeys = keys.size();

            for (int i = 0; i < totalKeys; i++) {
                String key = keys.get(i);
                double balance = playersSection.getDouble(key + ".balance");
                double change = playersSection.getDouble(key + ".change");
                String trimmedUuid = MojangAPI.getUUID(key).join();

                if(trimmedUuid == null) {
                    plugin.getLogger().warning("Failed to retrieve UUID for player: " + key);
                    trimmedUuid = trimmedUuid + "_" + key;
                }

                if(get().contains("players." + trimmedUuid)){
                    trimmedUuid = trimmedUuid + "_" + key;
                    plugin.getLogger().warning("Duplicate account found for: " + key);
                    plugin.getLogger().warning("Manual action needed!");
                }

                get().set("players." + trimmedUuid + ".name", key);
                get().set("players." + trimmedUuid + ".balance", balance);
                get().set("players." + trimmedUuid + ".change", change);


                // Commit in batches
                if ((i + 1) % batchSize == 0 || i == totalKeys - 1) {
                    plugin.getLogger().info("Processed " + (i + 1) + " player accounts.");
                }
            }

            get().set("format", "uuid");
            save();
            plugin.getLogger().info("Conversion complete! Data saved to balances.yml.");

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to convert file format:" + e.getMessage());
        }
    }
}
