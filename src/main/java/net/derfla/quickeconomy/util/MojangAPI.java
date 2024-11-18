package net.derfla.quickeconomy.util;

import net.derfla.quickeconomy.Main;
import org.bukkit.plugin.Plugin;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MojangAPI {

    static Plugin plugin = Main.getInstance();

    public static String getUUID(String playerName) {
        try {
            // Create the complete URL with the username
            URL url = new URL("https://api.mojang.com/users/profiles/minecraft/" + playerName);

            // Open the connection to the URL
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            // Check if the response code is 200 (HTTP OK)
            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                // Read the response from the input stream
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String inputLine;
                StringBuilder content = new StringBuilder();

                while ((inputLine = in.readLine()) != null) {
                    content.append(inputLine);
                }

                // Close connections
                in.close();
                connection.disconnect();

                // Parse JSON
                String jsonString = content.toString();
                JSONParser parser = new JSONParser();
                JSONObject json = (JSONObject) parser.parse(jsonString);
                return (String) json.get("id"); // Returning the player's UUID
            } else {
                plugin.getLogger().warning("Error: Username not found or server error.");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error: Username not found or server error: " + e.getMessage());
        }
        return null;
    }

    public static String getName(String uuid) {
        try {
            // Create the complete URL with the UUID
            URL url = new URL("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid);

            // Open the connection to the URL
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            // Check if the response code is 200 (HTTP OK)
            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                // Read the response from the input stream
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String inputLine;
                StringBuilder content = new StringBuilder();

                while ((inputLine = in.readLine()) != null) {
                    content.append(inputLine);
                }

                // Close connections
                in.close();
                connection.disconnect();

                // Parse JSON
                String jsonString = content.toString();
                JSONParser parser = new JSONParser();
                JSONObject json = (JSONObject) parser.parse(jsonString);

                return (String) json.get("name"); // Returning the player's name
            } else {
                plugin.getLogger().warning("Error: UUID not found or server error.");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error: UUID not found or server error: " + e.getMessage());
        }
        return null;
    }

}
