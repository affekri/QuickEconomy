package net.derfla.quickeconomy.listener;

import net.derfla.quickeconomy.Main;
import net.derfla.quickeconomy.file.BalanceFile;
import net.derfla.quickeconomy.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerJoinListener implements Listener {

    float change = 0.0f;

    @EventHandler
    public void onPlayerJoin (PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Translation.init(player);
        String uuid = TypeChecker.trimUUID(String.valueOf(player.getUniqueId()));
        if (Main.getInstance().getConfig().getBoolean("player.op.updateMessage")) {
            if(player.isOp() && DerflaAPI.updateAvailable()){
                player.sendMessage(Component.translatable("quickeconomy.update").style(Styles.INFOSTYLE).clickEvent(ClickEvent.openUrl("https://modrinth.com/plugin/quickeconomy/")));
            }
        }
        if (Main.SQLMode) {
            if (!DatabaseManager.accountExists(uuid)) {
                DatabaseManager.addAccount(String.valueOf(player.getUniqueId()), player.getName(), 0, 0);
            } else {
                DatabaseManager.updatePlayerName(player.getUniqueId().toString(), player.getName());
                // Check for change and send welcome message
                if (Main.getInstance().getConfig().getBoolean("player.welcomeMessage")) {
                    double change = DatabaseManager.getPlayerBalanceChange(uuid);
                    if (change != 0.0f) {
                        player.sendMessage(Component.translatable("player.welcome", Component.text(change)).style(Styles.INFOSTYLE));
                        DatabaseManager.setPlayerBalanceChange(uuid, 0.0f);
                    }
                }
                // Check for empty shops and notify the player
                if (Main.getInstance().getConfig().getBoolean("shop.emptyShopListJoin")) {
                    List<String> emptyShops = DatabaseManager.displayEmptyShopsView(uuid);
                    if (emptyShops != null && !emptyShops.isEmpty()) {
                        int emptyShopCount = emptyShops.size();
                        player.sendMessage(Component.translatable("shop.inventory.empty.list", Component.text(emptyShopCount), Component.text(String.join(", ", emptyShops))).style(Styles.INFOSTYLE));
                    }
                }
            }
        } else {
            FileConfiguration file = BalanceFile.get();
            if (file == null) {
                Bukkit.getLogger().warning("balances.yml not found!");
                return;
            }
            if (!Balances.hasAccount(uuid)) {
                file.set("players." + uuid + ".name", player.getName());
                BalanceFile.save();
                return;
            }
            if (!(file.contains("players." + uuid + ".change"))) {
                return;
            }
            // Check for change and send welcome message
            if (Main.getInstance().getConfig().getBoolean("player.welcomeMessage")) {
                change = Balances.getPlayerBalanceChange(uuid);
                if (change == 0.0f) {
                    return;
                }
                player.sendMessage(Component.translatable("player.welcomeback", Component.text(change)).style(Styles.INFOSTYLE));
            }
            Balances.setPlayerBalanceChange(uuid, 0.0f);
        }
    }
}
