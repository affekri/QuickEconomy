package net.derfla.quickeconomy.util;

import net.kyori.adventure.text.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        String componentString = component.toString();
        // Extract the text content within quotation marks
        int startIndex = componentString.indexOf('"') + 1;
        int endIndex = componentString.lastIndexOf('"');
        return componentString.substring(startIndex, endIndex);
    }
}
