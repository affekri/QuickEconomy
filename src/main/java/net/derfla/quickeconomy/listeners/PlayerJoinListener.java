package net.derfla.quickeconomy.listeners;

import net.derfla.quickeconomy.files.balanceFile;
import net.derfla.quickeconomy.utils.balances;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerJoinListener implements Listener {

    @EventHandler
    public void onPlayerJoin (PlayerJoinEvent event) {
        Player player = event.getPlayer();
        FileConfiguration file = balanceFile.get();
        if (file == null) {
            Bukkit.getLogger().warning("balances.yml not found!");
            return;
        }
        if (!(file.contains("players." + player.getName() + ".change"))) {
            return;
        }
        float change = balances.getPlayerBalanceChange(player.getName());
        if (change == 0.0f) {
            return;
        }
        player.sendMessage("§eWelcome back! While you were away you received " + change + " coins!");
        balances.setPlayerBalanceChange(player.getName(), 0.0f);
    }
}
