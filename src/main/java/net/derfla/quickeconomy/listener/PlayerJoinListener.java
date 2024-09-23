package net.derfla.quickeconomy.listener;

import net.derfla.quickeconomy.Main;
import net.derfla.quickeconomy.file.BalanceFile;
import net.derfla.quickeconomy.util.*;
import net.kyori.adventure.text.Component;
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
        if (Main.SQLMode) {
            DatabaseManager.addAccount(String.valueOf(player.getUniqueId()), player.getName(), 0, 0);
        } else {
            FileConfiguration file = BalanceFile.get();
            if (file == null) {
                Bukkit.getLogger().warning("balances.yml not found!");
                return;
            }
            if (!Balances.hasAccount(uuid)) {
                file.set("players." + uuid + ".name", player.getName());
                return;
            }
            if (!(file.contains("players." + uuid + ".change"))) {
                return;
            }
            change = Balances.getPlayerBalanceChange(uuid);
            if (change == 0.0f) {
                return;
            }
            Balances.setPlayerBalanceChange(uuid, 0.0f);
        }
        player.sendMessage(Component.translatable("player.welcomeback", Component.text(change)).style(Styles.INFOSTYLE));
    }
}
