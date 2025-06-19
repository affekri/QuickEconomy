package net.derfla.quickeconomy.listener;

import net.derfla.quickeconomy.Main;
import net.derfla.quickeconomy.database.Shop;
import net.derfla.quickeconomy.database.TableManagement;
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

        // Handle player account and balance updates asynchronously
        CompletableFuture.runAsync(() -> {
            try {
                // Check and update account
                if (Balances.hasAccountUUID(uuid)) {
                    Balances.updatePlayerName(uuid, player.getName());
                } else {
                    Balances.addAccount(uuid, player.getName());
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
                    Shop.displayEmptyShopsView(uuid)
                        .thenAccept(emptyShops -> {
                            if (emptyShops != null && !emptyShops.isEmpty()) {
                                int emptyShopCount = emptyShops.size();
                                String shopCoordinates = String.join(", ", emptyShops);
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
