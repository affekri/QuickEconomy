package net.derfla.quickeconomy.listener;

import com.destroystokyo.paper.event.player.PlayerClientOptionsChangeEvent;
import net.derfla.quickeconomy.util.Translation;
import org.bukkit.event.Listener;

public class PlayerChangeSettingsListener implements Listener {

    public void onPlayerChangeSettings(PlayerClientOptionsChangeEvent event) {
        Translation.init(event.getPlayer());
    }
}
