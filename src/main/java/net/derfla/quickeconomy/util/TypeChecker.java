package net.derfla.quickeconomy.util;

public class TypeChecker {

    public static boolean isFloat(String string) {
        try {
            Float.parseFloat(string);
        } catch (Exception e) {
            return false;
        }
        return true;
    }
}
