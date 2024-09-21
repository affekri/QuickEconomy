package net.derfla.quickeconomy.file;

import net.derfla.quickeconomy.Main;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.*

public class BalanceFile {

    private static File file;
    private static FileConfiguration customFile;
    static Plugin plugin = Main.getInstance();

    public static void setup(){
        // Setup logic, gets called when plugin is loaded
        file = new File(plugin.getDataFolder(), "balance.yml");

        if(!file.exists()){
            try {
                file.createNewFile();
                plugin.getLogger().info("Created new file: " + file.getName());
            } catch (IOException e) {
                // Just sends out a warning
                e.printStackTrace();
                plugin.getLogger().warning("Couldn't create file: " + file.getName());
            }
        }
        customFile = YamlConfiguration.loadConfiguration(file);
    }

    public static FileConfiguration get(){
        // Gets the file
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

    public static void reload(){
        // Reloads the file
        customFile = YamlConfiguration.loadConfiguration(file);
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

