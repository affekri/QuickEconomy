package net.derfla.quickeconomy.util;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.translation.GlobalTranslator;
import net.kyori.adventure.translation.TranslationStore;
import net.kyori.adventure.util.UTF8ResourceBundleControl;
import org.bukkit.entity.Player;

import java.text.MessageFormat;
import java.util.ResourceBundle;

public class Translation {

    public static void init(Player player) {
        TranslationStore<MessageFormat> store = TranslationStore.messageFormat(Key.key("namespace:value"));

        ResourceBundle bundle = ResourceBundle.getBundle("translations.Translation", player.locale(), UTF8ResourceBundleControl.get());
        store.registerAll(player.locale(), bundle.keySet(), key -> new MessageFormat(bundle.getString((String) key)));
        GlobalTranslator.translator().addSource(store);
    }

}
