package net.derfla.quickeconomy.listener;

import net.derfla.quickeconomy.Main;
import net.derfla.quickeconomy.database.Shop;
import net.derfla.quickeconomy.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class PlayerJoinListener implements Listener {

    static Plugin plugin = Main.getInstance();

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Translation.init(player);
        String uuid = TypeChecker.trimUUID(String.valueOf(player.getUniqueId()));

        // Handle update message
        if (plugin.getConfig().getBoolean("player.op.updateMessage")) {
            if (player.isOp() && DerflaAPI.updateAvailable()) {
                player.sendMessage(Component.translatable("quickeconomy.update")
                    .style(Styles.INFOSTYLE)
                    .clickEvent(ClickEvent.openUrl("https://modrinth.com/plugin/quickeconomy/")));
            }
        }

        CompletableFuture<Void> accountSetupFuture = CompletableFuture.runAsync(() -> {
            if (Balances.hasAccount(uuid)) {
                Balances.updatePlayerName(uuid, player.getName());
                if (!AccountCache.accountExists(uuid)) {
                    // Account exists in database but not in cache, wait for cache to be updated
                    try {
                        Thread.sleep(100); // Short wait for cache update
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        plugin.getLogger().warning("Thread interrupted while waiting for account cache for " + player.getName());
                    }
                }
            } else {
                Balances.addAccount(uuid, player.getName());
                // Ensure the account is fully added to cache before proceeding
                int retries = 0;
                while (!AccountCache.accountExists(uuid) && retries < 10) {
                    try {
                        Thread.sleep(50); // Wait for account to be added to cache
                        retries++;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        plugin.getLogger().warning("Thread interrupted while waiting for new account cache for " + player.getName());
                        break;
                    }
                }
                if (!AccountCache.accountExists(uuid)) {
                    plugin.getLogger().severe("Failed to add account to cache for player " + player.getName() + " after " + retries + " retries");
                }
            }
        }, Main.getExecutorService());

        accountSetupFuture.thenRunAsync(() -> {
            try {
                // Verify account is in cache before proceeding with balance operations
                if (!AccountCache.accountExists(uuid)) {
                    plugin.getLogger().severe("Account not found in cache for player " + player.getName() + ", skipping balance operations");
                    return;
                }

                // Handle welcome message and balance change
                if (plugin.getConfig().getBoolean("player.welcomeMessage")) {
                    double change = Balances.getPlayerBalanceChange(uuid);
                    if (change > 0) {
                        Bukkit.getScheduler().runTask(plugin, () -> 
                            player.sendMessage(Component.translatable("player.welcomeback", 
                                Component.text(change)).style(Styles.INFOSTYLE)));
                    }
                }
                Balances.setPlayerBalanceChange(uuid, 0.0f);

                // Handle empty shops list if enabled
                if (Main.SQLMode && plugin.getConfig().getBoolean("shop.emptyShopListJoin")) {
                    Shop.displayShopsView(uuid)
                    .thenCompose(shops -> {
                        List<String> emptyShopCoordinates = shops.stream()
                            .filter(shop -> shop.contains("(1)"))  // Filter for empty shops
                            .map(shop -> shop.substring(0, shop.indexOf(" (")))  // Extract coordinates part only
                            .collect(Collectors.toList());
                        return CompletableFuture.completedFuture(emptyShopCoordinates);
                    })
                    .thenAccept(emptyShopCoordinates -> {
                        if (emptyShopCoordinates != null && !emptyShopCoordinates.isEmpty()) {
                            int emptyShopCount = emptyShopCoordinates.size();
                            String shopCoordinates = String.join(", ", emptyShopCoordinates);
                            Bukkit.getScheduler().runTask(plugin, () -> 
                                player.sendMessage(Component.translatable("shop.inventory.empty.list", 
                                    Component.text(emptyShopCount), Component.text(shopCoordinates)).style(Styles.INFOSTYLE)));
                        }
                    })
                    .exceptionally(ex -> {
                        plugin.getLogger().warning("Failed to get empty shops for player " + 
                            player.getName() + ": " + ex.getMessage());
                        return null;
                    });
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Error processing player join for " + 
                    player.getName() + ": " + e.getMessage());
            }
        }, Main.getExecutorService());
    }
}
