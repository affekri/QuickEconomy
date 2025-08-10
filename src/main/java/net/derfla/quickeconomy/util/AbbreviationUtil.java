package net.derfla.quickeconomy.util;

import org.jetbrains.annotations.NotNull;

public class AbbreviationUtil {

    /**
     * Converts a string with abbreviations (k, m, b) to a numeric value.
     * For example, "1k" becomes 1000.0, "2.5m" becomes 2500000.0.
     *
     * @param amount The string amount to convert.
     * @return The numeric value of the string.
     * @throws NumberFormatException If the string contains an invalid number format.
     * @throws IllegalArgumentException If the string contains an invalid abbreviation.
     */
    public static double fromString(@NotNull String amount) throws NumberFormatException, IllegalArgumentException {
        if (amount == null || amount.isEmpty()) {
            throw new NumberFormatException("Input amount cannot be null or empty.");
        }

        amount = amount.toLowerCase();
        char lastChar = amount.charAt(amount.length() - 1);
        String numberPart = amount.substring(0, amount.length() - 1);

        double multiplier = 1.0;

        if (!Character.isDigit(lastChar)) {
            switch (lastChar) {
                case 'k':
                    multiplier = 1_000;
                    break;
                case 'm':
                    multiplier = 1_000_000;
                    break;
                case 'b':
                    multiplier = 1_000_000_000;
                    break;
                default:
                    throw new IllegalArgumentException("Invalid abbreviation: " + lastChar);
            }
        } else {
            numberPart = amount;
        }
        
        try {
            return Double.parseDouble(numberPart) * multiplier;
        } catch (NumberFormatException e) {
            throw new NumberFormatException("Invalid number format: " + numberPart);
        }
    }
}
