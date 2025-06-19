package net.derfla.quickeconomy.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.time.ZoneOffset;


public class TypeChecker {

    public static boolean isDouble(String string) {
        try {
            Double.parseDouble(string);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public static double formatDouble(double inputDouble) {
        // Extract integer and decimal parts
        int integerPart = (int) Math.floor(inputDouble);
        double decimalPart = inputDouble - integerPart;

        // Multiply decimal part by 100 to get two decimal places as integers
        int decimalPartInt = (int) Math.round(decimalPart * 100);

        // Combine integer and decimal parts with "."
        String formattedDouble = String.format("%d.%02d", integerPart, decimalPartInt);
        return Double.parseDouble(formattedDouble);
    }

    public static String getRawString(Component component) {
        return ((TextComponent) component).content();
    }

    public static String trimUUID(String uuid) {
        if (uuid == null) {
            return null;
        }
        if (uuid.length() == 36) {
            return uuid.replaceAll("-", "");
        }
        else if (uuid.length() == 32) {
            return uuid;
        }
        else {
            throw new IllegalArgumentException("Invalid UUID format: " + uuid);
        }
    }

    public static String untrimUUID(String uuid) {
        if (uuid == null) {
            return null;
        }
        if(uuid.isEmpty()){
            return null;
        }
        if (uuid.length() == 32) {
            return uuid.replaceAll("(.{8})(.{4})(.{4})(.{4})(.{12})", "$1-$2-$3-$4-$5");
        } 
        else if (uuid.length() == 36) {
            return uuid;
        }
        else {
            throw new IllegalArgumentException("Invalid UUID format: " + uuid);
        }
    }

    // Comparing version1 on to version2. Returns true if version1 is newer
    public static boolean isNewerVersion(String version1, String version2) {
        // Split by "-" and use only the main version part (before the dash)
        String mainVersion1 = version1.split("-")[0];
        String mainVersion2 = version2.split("-")[0];

        String[] v1Parts = mainVersion1.split("\\.");
        String[] v2Parts = mainVersion2.split("\\.");

        int maxLength = Math.max(v1Parts.length, v2Parts.length);

        for (int i = 0; i < maxLength; i++) {
            int v1Part = i < v1Parts.length ? Integer.parseInt(v1Parts[i]) : 0;
            int v2Part = i < v2Parts.length ? Integer.parseInt(v2Parts[i]) : 0;

            if (v1Part > v2Part) {
                return true;
            } else if (v1Part < v2Part) {
                return false;
            }
        }
        return false;
    }

    public static String convertToUTC(String dateString) {
        // Parse the input date string to LocalDateTime
        LocalDateTime localDateTime = LocalDateTime.parse(dateString, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        // Convert LocalDateTime to ZonedDateTime in UTC
        ZonedDateTime utcDateTime = localDateTime.atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC);
        // Format the UTC date-time to a string
        return utcDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    public static String convertToLocalTime(String dateString) {
        // Parse the input date string to LocalDateTime
        LocalDateTime localDateTime;
        try {
            // Try parsing with milliseconds first
            localDateTime = LocalDateTime.parse(dateString, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
        } catch (Exception e) {
            try {
                // Fall back to parsing without milliseconds
                localDateTime = LocalDateTime.parse(dateString, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            } catch (Exception ex) {
                throw new RuntimeException("Unable to parse date string: " + dateString, ex);
            }
        }
        // Convert LocalDateTime to ZonedDateTime in UTC
        ZonedDateTime utcDateTime = localDateTime.atZone(ZoneOffset.UTC);
        // Convert UTC ZonedDateTime to the system's default time zone
        ZonedDateTime localDateTimeZone = utcDateTime.withZoneSameInstant(ZoneId.systemDefault());
        // Format the local date-time to a string
        return localDateTimeZone.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }


}
