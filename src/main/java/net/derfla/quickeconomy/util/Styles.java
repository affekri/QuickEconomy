package net.derfla.quickeconomy.util;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Utility class containing predefined text styles for consistent formatting throughout the QuickEconomy plugin.
 * This class provides various Adventure API {@link Style} constants for different UI elements and message types.
 * 
 * <p>All styles use the Adventure API text formatting system for cross-platform compatibility
 * and modern text rendering features.</p>
 * 
 * <p>This is a utility class with only static constants and cannot be instantiated.</p>
 * 
 * @author QuickEconomy
 * @version 1.0
 * @since 1.0
 */
public final class Styles {
    
    /**
     * Style for error messages.
     * Uses red color to indicate errors, warnings, or failed operations.
     */
    public static final Style ERRORSTYLE = Style.style(NamedTextColor.RED);
    
    /**
     * Style for informational messages.
     * Uses yellow color to indicate general information or status updates.
     */
    public static final Style INFOSTYLE = Style.style(NamedTextColor.YELLOW);
    
    /**
     * Style for bank-related headers and titles.
     * Uses gold color with bold formatting for bank interface elements.
     */
    public static final Style BANKHEADER = Style.style(NamedTextColor.GOLD, TextDecoration.BOLD);
    
    /**
     * Style for shop-related headers and titles.
     * Uses aqua color with bold formatting for shop interface elements.
     */
    public static final Style SHOPHEADER = Style.style(NamedTextColor.AQUA, TextDecoration.BOLD);
    
    /**
     * Style for body text and general content.
     * Uses white color for standard readable text content.
     */
    public static final Style BODY = Style.style(NamedTextColor.WHITE);
    
    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private Styles() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}
