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

public class BalanceFile {

    private static File file;
    private static FileConfiguration customFile;
    static Plugin plugin = Main.getInstance();

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

    public static FileConfiguration get() {
        if (customFile == null) {
            plugin.getLogger().severe("Custom file not initialized. Did you call setup()?");
        }
        return customFile;
    }

    public static void save(){
        // Saves the file
        try {
            customFile.save(file);
        } catch (IOException e) {
            // Just sends out a warning
            plugin.getLogger().warning("Couldn't save file: " + file.getName());
        }
    }

    public static void reload() {
        // Reloads the file
        if (file.exists()) {
            customFile = YamlConfiguration.loadConfiguration(file);
            plugin.getLogger().info("File reloaded successfully: " + file.getName());
        } else {
            plugin.getLogger().warning("File does not exist, cannot reload: " + file.getName());
        }
    }

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

    public static String checkFormat() {
        if(!get().contains("format")) return "playerName";
        return get().getString("format");
    }

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
                String trimmedUuid = MojangAPI.getUUID(key);

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
