package net.derfla.quickeconomy.util;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;

public final class Styles {
    public static final Style ERRORSTYLE = Style.style(NamedTextColor.RED);
    public static final Style INFOSTYLE = Style.style(NamedTextColor.YELLOW);
    public static final Style BANKHEADER = Style.style(NamedTextColor.GOLD, TextDecoration.BOLD);
    public static final Style SHOPHEADER = Style.style(NamedTextColor.AQUA, TextDecoration.BOLD);
    public static final Style BODY = Style.style(NamedTextColor.WHITE);
}
