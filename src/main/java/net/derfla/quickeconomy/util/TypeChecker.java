package net.derfla.quickeconomy.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Utility class providing various type checking, validation, and conversion methods for the QuickEconomy plugin.
 * This class contains static methods for handling common data validation and transformation operations
 * including number validation, UUID formatting, version comparison, and datetime conversions.
 * 
 * <p>This utility class centralizes validation logic to ensure consistent data handling throughout
 * the plugin and provides safe conversion methods that handle edge cases appropriately.</p>
 * 
 * <p>This is a utility class with only static methods and cannot be instantiated.</p>
 * 
 * @author QuickEconomy
 * @version 1.0
 * @since 1.0
 */
public class TypeChecker {

    /**
     * Checks if a string can be parsed as a valid double value.
     * 
     * @param string the string to validate
     * @return {@code true} if the string can be parsed as a double, {@code false} otherwise
     */
    public static boolean isDouble(String string) {
        try {
            Double.parseDouble(string);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    /**
     * Formats a double value to have exactly two decimal places.
     * This method rounds the input to two decimal places and returns the formatted result.
     * 
     * @param inputDouble the double value to format
     * @return the formatted double with exactly two decimal places
     */
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

    /**
     * Extracts the raw string content from an Adventure API Text Component.
     * 
     * @param component the text component to extract content from
     * @return the raw string content of the component
     * @throws ClassCastException if the component is not a TextComponent
     */
    public static String getRawString(Component component) {
        return ((TextComponent) component).content();
    }

    /**
     * Removes dashes from a UUID string to create a trimmed 32-character format.
     * Accepts both standard UUID format (36 characters with dashes) and already trimmed format (32 characters).
     * 
     * @param uuid the UUID string to trim (can be 36 or 32 characters)
     * @return the trimmed UUID string without dashes, or null if input is null
     * @throws IllegalArgumentException if the UUID format is invalid (not 32 or 36 characters)
     */
    public static String trimUUID(String uuid) {
        if (uuid == null) {
            return null;
        }
        if (uuid.length() == 36) {
            return uuid.replace("-", "");
        }
        else if (uuid.length() == 32) {
            return uuid;
        }
        else {
            throw new IllegalArgumentException("Invalid UUID format: " + uuid);
        }
    }

    /**
     * Adds dashes to a trimmed UUID string to create the standard 36-character UUID format.
     * Accepts both trimmed format (32 characters) and standard format (36 characters with dashes).
     * 
     * @param uuid the UUID string to format (can be 32 or 36 characters)
     * @return the standard UUID string with dashes, or null if input is null or empty
     * @throws IllegalArgumentException if the UUID format is invalid (not 32 or 36 characters)
     */
    public static String untrimUUID(String uuid) {
        if (uuid == null) {
            return null;
        }
        if(uuid.isEmpty()){
            return null;
        }
        if (uuid.length() == 32) {
            return uuid.substring(0, 8) + "-" +
                   uuid.substring(8, 12) + "-" +
                   uuid.substring(12, 16) + "-" +
                   uuid.substring(16, 20) + "-" +
                   uuid.substring(20, 32);
        }
        else if (uuid.length() == 36) {
            return uuid;
        }
        else {
            throw new IllegalArgumentException("Invalid UUID format: " + uuid);
        }
    }

    /**
     * Compares two version strings to determine if the first version is newer than the second.
     * Supports semantic versioning format (e.g., "1.2.3" or "1.2.3-SNAPSHOT").
     * Version suffixes after dashes are ignored for comparison purposes.
     * 
     * @param version1 the first version string to compare
     * @param version2 the second version string to compare
     * @return {@code true} if version1 is newer than version2, {@code false} otherwise
     * @throws NumberFormatException if version parts cannot be parsed as integers
     */
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

    /**
     * Converts a local datetime string to UTC format.
     * The input is assumed to be in the system's default timezone.
     * 
     * @param dateString the local datetime string in format "yyyy-MM-dd HH:mm:ss"
     * @return the UTC datetime string in format "yyyy-MM-dd HH:mm:ss"
     * @throws java.time.format.DateTimeParseException if the input string cannot be parsed
     */
    public static String convertToUTC(String dateString) {
        // Parse the input date string to LocalDateTime
        LocalDateTime localDateTime = LocalDateTime.parse(dateString, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        // Convert LocalDateTime to ZonedDateTime in UTC
        ZonedDateTime utcDateTime = localDateTime.atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC);
        // Format the UTC date-time to a string
        return utcDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /**
     * Converts a UTC datetime string to local time format based on the system's default timezone.
     * Supports datetime strings with or without milliseconds.
     * 
     * @param dateString the UTC datetime string in format "yyyy-MM-dd HH:mm:ss" or "yyyy-MM-dd HH:mm:ss.SSS"
     * @return the local datetime string in format "yyyy-MM-dd HH:mm:ss"
     * @throws RuntimeException if the input string cannot be parsed in either supported format
     */
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

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private TypeChecker() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}
