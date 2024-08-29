package net.derfla.quickeconomy.listener;

import net.derfla.quickeconomy.file.BalanceFile;
import net.derfla.quickeconomy.util.Balances;
import net.derfla.quickeconomy.util.Styles;
import net.derfla.quickeconomy.util.Translation;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerJoinListener implements Listener {

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Check if this is the player's first join
        if (!player.hasPlayedBefore()) {
            Main.getInstance().addAccount(player.getUniqueId().toString(), player.getName());
            // You might want to add a welcome message or initial balance here
        }
        if (!(file.contains("players." + player.getName() + ".change"))) {
            return;
        }
        float change = Balances.getPlayerBalanceChange(player.getName());
        if (change == 0.0f) {
            return;
        }
        player.sendMessage(Component.translatable("player.welcomeback", Component.text(change)).style(Styles.INFOSTYLE));
        Balances.setPlayerBalanceChange(player.getName(), 0.0f);
    }
}
