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

/**
 * Utility class for interacting with Derfla's API services to check for plugin updates.
 * This class provides functionality to query the official Derfla API endpoint for the latest
 * QuickEconomy plugin version and compare it with the currently running version.
 * 
 * <p>The API endpoint returns version information in JSON format, which is parsed and
 * compared using semantic versioning rules through the {@link TypeChecker#isNewerVersion(String, String)} method.</p>
 * 
 * <p>This utility is used for automatic update notifications and version checking functionality
 * within the plugin.</p>
 * 
 * @author QuickEconomy
 * @version 1.0
 * @since 1.0
 * @see TypeChecker#isNewerVersion(String, String)
 */
public class DerflaAPI {

    private static final Plugin plugin = Main.getInstance();
    private static final String VERSION_URL = "https://derfla.net/api/qe.json";
    private static final String CURRENT_VERSION = plugin.getPluginMeta().getVersion();
    private static final ObjectMapper objectMapper = new ObjectMapper();


    /**
     * Checks if a newer version of the QuickEconomy plugin is available by querying the Derfla API.
     * This method performs a synchronous HTTP request to retrieve the latest version information
     * and compares it with the currently running plugin version.
     * 
     * <p>The method follows these steps:
     * <ul>
     *   <li>Makes an HTTP GET request to the Derfla API endpoint</li>
     *   <li>Parses the JSON response to extract the latest version</li>
     *   <li>Compares the latest version with the current version using semantic versioning</li>
     * </ul>
     * </p>
     * 
     * <p><strong>Note:</strong> This method performs a blocking network operation and should
     * be called from an async context or background thread to avoid blocking the main server thread.</p>
     * 
     * @return {@code true} if a newer version is available, {@code false} if the current version
     *         is up to date or if an error occurs during the check
     * 
     * @see TypeChecker#isNewerVersion(String, String)
     */
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
    
    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private DerflaAPI() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}
