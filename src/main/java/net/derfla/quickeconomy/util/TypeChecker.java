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

    public static float formatFloat(float inputFloat) {
        // Extract integer and decimal parts
        int integerPart = (int) Math.floor(inputFloat);
        float decimalPart = inputFloat - integerPart;

        // Multiply decimal part by 100 to get two decimal places as integers
        int decimalPartInt = (int) Math.round(decimalPart * 100);

        // Combine integer and decimal parts with "."
        String formattedFloat = String.format("%d.%02d", integerPart, decimalPartInt);
        return Float.parseFloat(formattedFloat);
    }


    public static String getRawString(Component component) {
        return ((TextComponent) component).content();
    }
}
