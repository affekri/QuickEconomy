package net.derfla.quickeconomy.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;


public class TypeChecker {

    public static boolean isFloat(String string) {
        try {
            Float.parseFloat(string);
        } catch (Exception e) {
            return false;
        }
        return true;
    }


    public static String getRawString(Component component) {
        return ((TextComponent) component).content();
    }
}
