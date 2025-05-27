package net.derfla.quickeconomy.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.derfla.quickeconomy.Main;
import org.bukkit.plugin.Plugin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

public class DerflaAPI {

    private static final Plugin plugin = Main.getInstance();
    private static final String VERSION_URL = "https://derfla.net/api/qe.json";
    private static final String CURRENT_VERSION = plugin.getPluginMeta().getVersion();
    private static final ObjectMapper objectMapper = new ObjectMapper();


    public static boolean updateAvailable() {
        try {
            // Create a URL object and open connection
            URL url = new URI(VERSION_URL).toURL();
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
            JsonNode json = objectMapper.readTree(jsonString);
            String latestVersion = json.get("version").asText();

            return TypeChecker.isNewerVersion(latestVersion, CURRENT_VERSION);

        } catch (Exception e) {
            plugin.getLogger().info("Error while checking for updates: " + e.getMessage());
        }
        return false;
    }
}
