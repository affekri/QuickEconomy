package net.derfla.quickeconomy.util;

import net.derfla.quickeconomy.Main;
import org.bukkit.plugin.Plugin;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Utility class for interacting with Mojang's official API services.
 * This class provides asynchronous methods to retrieve player information from Mojang's servers,
 * including UUID-to-name and name-to-UUID conversions.
 * 
 * <p>All API calls are performed asynchronously using {@link CompletableFuture} to prevent
 * blocking the main server thread. The methods handle HTTP requests and JSON parsing
 * automatically.</p>
 * 
 * <p>This utility class uses Mojang's official public APIs:
 * <ul>
 *   <li>Profile API: https://api.mojang.com/users/profiles/minecraft/{username}</li>
 *   <li>Session API: https://sessionserver.mojang.com/session/minecraft/profile/{uuid}</li>
 * </ul>
 * </p>
 * 
 * <p><strong>Rate Limiting:</strong> Be aware that Mojang APIs have rate limits.
 * Excessive requests may result in temporary IP bans.</p>
 * 
 * @author QuickEconomy
 * @version 1.0
 * @since 1.0
 * @see CompletableFuture
 */
public class MojangAPI {

    static Plugin plugin = Main.getInstance();
    private static final ExecutorService executorService = Main.getExecutorService();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Retrieves a player's UUID from their username using Mojang's Profile API.
     * This method performs an asynchronous HTTP request to Mojang's servers.
     * 
     * <p>The returned UUID is in trimmed format (32 characters without dashes).
     * Use {@link TypeChecker#untrimUUID(String)} if you need the standard format.</p>
     * 
     * @param playerName the Minecraft username to look up (case-insensitive)
     * @return a {@link CompletableFuture} that will complete with the player's UUID string,
     *         or null if the player is not found or an error occurs
     * @throws IllegalArgumentException if playerName is null or empty
     * 
     * @see <a href="https://wiki.vg/Mojang_API#Username_to_UUID">Mojang API Documentation</a>
     */
    public static CompletableFuture<String> getUUID(String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Create the complete URL with the username
                URL url = new URI("https://api.mojang.com/users/profiles/minecraft/" + playerName).toURL();

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
                    JsonNode json = objectMapper.readTree(jsonString);
                    return json.get("id").asText(); // Returning the player's UUID
                } else {
                    plugin.getLogger().warning("Error: Username not found or server error.");
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Error: Username not found or server error: " + e.getMessage());
            }
            return null;
        }, executorService);
    }

    /**
     * Retrieves a player's current username from their UUID using Mojang's Session API.
     * This method performs an asynchronous HTTP request to Mojang's servers.
     * 
     * <p>This method returns the most current username associated with the UUID,
     * which may be different from historical usernames if the player has changed their name.</p>
     * 
     * @param uuid the player's UUID string (accepts both trimmed and standard formats)
     * @return a {@link CompletableFuture} that will complete with the player's current username,
     *         or null if the UUID is not found or an error occurs
     * @throws IllegalArgumentException if uuid is null or empty
     * 
     * @see <a href="https://wiki.vg/Mojang_API#UUID_to_Profile_and_Skin.2FCape">Mojang API Documentation</a>
     */
    public static CompletableFuture<String> getName(String uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Create the complete URL with the UUID
                URL url = new URI("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid).toURL();

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
                    JsonNode json = objectMapper.readTree(jsonString);

                    return json.get("name").asText(); // Returning the player's name
                } else {
                    plugin.getLogger().warning("Error: UUID not found or server error.");
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Error: UUID not found or server error: " + e.getMessage());
            }
            return null;
        }, executorService);
    }
    
    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private MojangAPI() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}
