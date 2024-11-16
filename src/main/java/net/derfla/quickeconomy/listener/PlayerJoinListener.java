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
        if (Balances.hasAccount(uuid)) {
            Balances.updatePlayerName(uuid, player.getName());
        } else {
            Balances.addAccount(uuid, player.getName());
        }

        if (Main.getInstance().getConfig().getBoolean("player.welcomeMessage")) {
            if (Balances.getPlayerBalanceChange(uuid) <= 0) {
                player.sendMessage(Component.translatable("player.welcomeback", Component.text(change)).style(Styles.INFOSTYLE));
            }
        }
        Balances.setPlayerBalanceChange(uuid, 0.0f);
        if (!Main.SQLMode) return;
        if (!Main.getInstance().getConfig().getBoolean("shop.emptyShopListJoin")) return;
        List<String> emptyShops = DatabaseManager.displayEmptyShopsView(uuid);
        if (emptyShops == null || emptyShops.isEmpty()) return;
        int emptyShopCount = emptyShops.size();
        player.sendMessage(Component.translatable("shop.inventory.empty.list", Component.text(emptyShopCount), Component.text(String.join(", ", emptyShops))).style(Styles.INFOSTYLE));
    }
}
