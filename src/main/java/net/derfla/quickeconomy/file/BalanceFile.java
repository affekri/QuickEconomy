package net.derfla.quickeconomy.file;

import net.derfla.quickeconomy.Main;
import net.derfla.quickeconomy.util.MojangAPI;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

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
            // Load the original YAML file
            InputStream inputStream = Files.newInputStream(file.toPath());

            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(inputStream);

            // Convert player data to use UUIDs as keys
            Map<String, Map<String, Object>> newPlayersData = new HashMap<>();
            Map<String, Map<String, Object>> players = (Map<String, Map<String, Object>>) data.get("players");

            // Loop through each player and convert their name to UUID
            for (Map.Entry<String, Map<String, Object>> entry : players.entrySet()) {
                String playerName = entry.getKey();
                Map<String, Object> playerData = entry.getValue();

                String playerUUID = MojangAPI.getUUID(playerName);
                if (playerUUID != null) {
                    // Add UUID as key and include the player name in the new structure
                    Map<String, Object> newPlayerData = new HashMap<>();

                    newPlayerData.put("name", playerName); // Store the player's name
                    newPlayerData.put("balance", playerData.get("balance")); // Preserve balance
                    newPlayerData.put("change", playerData.get("change")); // Preserve change

                    newPlayersData.put(playerUUID, newPlayerData);
                } else {
                    plugin.getLogger().severe("Failed to retrieve UUID for player: " + playerName);
                }
            }
            // Create new structure
            Map<String, Object> newYamlData = new HashMap<>();
            newYamlData.put("players", newPlayersData);

            // Write the updated data back to the same YAML file
            DumperOptions options = new DumperOptions();
            options.setIndent(2);
            options.setPrettyFlow(true);
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            Yaml newYaml = new Yaml(options);

            FileWriter writer = new FileWriter("players.yml"); // Overwrite the same file
            newYaml.dump(newYamlData, writer);
            get().set("format", "uuid");
            save();
            plugin.getLogger().info("Conversion complete! Data saved to balances.yml.");

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to convert file format:" + e.getMessage());
        }
    }
}
