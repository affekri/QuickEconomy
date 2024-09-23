package net.derfla.quickeconomy.listener;

import net.derfla.quickeconomy.util.Balances;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerLeaveListener implements Listener {

    @EventHandler
    public void onPlayerLeave(PlayerQuitEvent event) {
        String uuid = event.getPlayer().getUniqueId().toString();
        Balances.setPlayerBalance(uuid, 0);
    }
}
