package net.derfla.quickeconomy.listener;

import net.derfla.quickeconomy.util.Balances;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Listener class that handles player leave events.
 * This listener is responsible for cleaning up player-specific data when a player disconnects.
 */
public class PlayerLeaveListener implements Listener {

    /**
     * Handles the event when a player leaves the server.
     * Resets the player's balance change tracking to 0 to prevent memory leaks
     * and ensure clean state for the next time the player joins.
     *
     * @param event The PlayerQuitEvent containing information about the player leaving
     */
    @EventHandler
    public void onPlayerLeave(PlayerQuitEvent event) {
        String uuid = event.getPlayer().getUniqueId().toString();
        Balances.setPlayerBalanceChange(uuid, 0);
    }
}
