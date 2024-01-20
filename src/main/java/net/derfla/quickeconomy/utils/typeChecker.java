package net.derfla.quickeconomy.utils;

public class typeChecker {

    public static boolean isFloat(String string) {
        try {
            Float.parseFloat(string);
        } catch (Exception e) {
            return false;
        }
        return true;
    }
}
