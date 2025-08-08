package net.derfla.quickeconomy.util;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.translation.GlobalTranslator;
import net.kyori.adventure.translation.TranslationStore;
import net.kyori.adventure.util.UTF8ResourceBundleControl;
import org.bukkit.entity.Player;

import java.text.MessageFormat;
import java.util.ResourceBundle;

/**
 * Utility class for managing player-specific translations and internationalization in the QuickEconomy plugin.
 * This class handles the initialization and registration of translation stores for different player locales.
 * 
 * <p>The translation system uses Adventure API's translation framework to provide localized messages
 * based on individual player's client locale settings. Translation files are loaded from the
 * {@code translations} resource bundle.</p>
 * 
 * <p>This utility integrates with Minecraft's client-side locale detection to automatically
 * provide the appropriate language for each player.</p>
 * 
 * @author QuickEconomy
 * @version 1.0
 * @since 1.0
 * @see net.kyori.adventure.translation.GlobalTranslator
 * @see java.util.ResourceBundle
 */
public class Translation {

    /**
     * Initializes and registers translations for a specific player based on their client locale.
     * This method creates a translation store using the player's locale and registers it with
     * the global translator for use throughout the plugin.
     * 
     * <p>The method performs the following operations:
     * <ul>
     *   <li>Creates a new {@link TranslationStore} with MessageFormat support</li>
     *   <li>Loads the appropriate resource bundle for the player's locale</li>
     *   <li>Registers all translation keys from the bundle</li>
     *   <li>Adds the translation store to the global translator</li>
     * </ul>
     * </p>
     * 
     * <p><strong>Note:</strong> This method should be called when a player joins the server
     * or when their locale needs to be refreshed.</p>
     * 
     * @param player the player for whom translations should be initialized.
     *               The player's {@link Player#locale()} is used to determine
     *               which translation bundle to load
     * @throws java.util.MissingResourceException if the translation bundle for the
     *                                           player's locale cannot be found
     * @throws IllegalArgumentException if the player parameter is null
     * 
     * @see Player#locale()
     * @see ResourceBundle#getBundle(String, java.util.Locale, java.util.ResourceBundle.Control)
     * @see TranslationStore#registerAll(java.util.Locale, java.util.Set, java.util.function.Function)
     */
    public static void init(Player player) {
        TranslationStore<MessageFormat> store = TranslationStore.messageFormat(Key.key("namespace:value"));

        ResourceBundle bundle = ResourceBundle.getBundle("translations.Translation", player.locale(), UTF8ResourceBundleControl.get());
        store.registerAll(player.locale(), bundle.keySet(), key -> new MessageFormat(bundle.getString((String) key)));
        GlobalTranslator.translator().addSource(store);
    }
    
    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private Translation() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}
