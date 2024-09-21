package net.derfla.quickeconomy.file;

import net.derfla.quickeconomy.Main;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.*;

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

}

