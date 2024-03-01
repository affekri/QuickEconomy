package net.derfla.quickeconomy.util;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.translation.GlobalTranslator;
import net.kyori.adventure.translation.TranslationRegistry;
import net.kyori.adventure.util.UTF8ResourceBundleControl;
import org.bukkit.entity.Player;

import java.util.ResourceBundle;

public class Translation {

    public static void init(Player player) {
        TranslationRegistry registry = TranslationRegistry.create(Key.key("namespace:value"));

        ResourceBundle translation = ResourceBundle.getBundle("translations.Translation", player.locale(), UTF8ResourceBundleControl.get());
        registry.registerAll(player.locale(), translation, true);
        GlobalTranslator.translator().addSource(registry);
    }

}
