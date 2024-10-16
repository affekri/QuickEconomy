package net.derfla.quickeconomy.util;

import net.derfla.quickeconomy.Main;
import org.bukkit.plugin.Plugin;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class DerflaAPI {

    private static final Plugin plugin = Main.getInstance();
    private static final String VERSION_URL = "https://derfla.net/api/qe.json";
    private static final String CURRENT_VERSION = plugin.getDescription().getVersion();


    public static boolean updateAvailable() {
        try {
            // Create a URL object and open connection
            URL url = new URL(VERSION_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            // Read the response
            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String inputLine;
            StringBuilder content = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine);
            }
            in.close();
            connection.disconnect();

            // Parse the JSON
            String jsonString = content.toString();
            JSONParser parser = new JSONParser();
            JSONObject json = (JSONObject) parser.parse(jsonString);
            String latestVersion = (String) json.get("version");

            return !CURRENT_VERSION.equalsIgnoreCase(latestVersion);

        } catch (Exception e) {
            plugin.getLogger().info("Error while checking for updates: " + e.getMessage());
        }
        return false;
    }
}
