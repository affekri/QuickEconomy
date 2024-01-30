package net.derfla.quickeconomy.file;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;

public class BalanceFile {

    private static File file;
    private static FileConfiguration customFile;

    public static void setup(){
        // Setup logic, gets called when plugin is loaded
        file = new File(Bukkit.getServer().getPluginManager().getPlugin("QuickEconomy").getDataFolder(), "balance.yml");

        if(!file.exists()){
            try {
                file.createNewFile();
                Bukkit.getLogger().info("Created new file: " + file.getName());
            } catch (IOException e) {
                // Just sends out a warning
                e.printStackTrace();
                Bukkit.getLogger().warning("Couldn't create file: " + file.getName());
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
            Bukkit.getLogger().warning("Couldn't save file: " + file.getName());
        }
    }

    public static void reload(){
        // Reloads the file
        customFile = YamlConfiguration.loadConfiguration(file);
    }
}

