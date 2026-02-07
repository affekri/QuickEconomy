package net.derfla.quickeconomy.util;

import org.jetbrains.annotations.NotNull;

/**
 * Utility for parsing monetary amounts that may include magnitude abbreviations.
 *
 * <p>Players can specify coin amounts using shorthand suffixes instead of typing
 * out full numbers. The following abbreviations are supported (case-insensitive):
 * <ul>
 *   <li><b>K</b> – thousand (× 1,000)</li>
 *   <li><b>M</b> – million  (× 1,000,000)</li>
 *   <li><b>B</b> – billion  (× 1,000,000,000)</li>
 *   <li><b>T</b> – trillion (× 1,000,000,000,000)</li>
 * </ul>
 *
 * <p>Examples: {@code "1.5K"} → 1500.0, {@code "2M"} → 2000000.0, {@code "500"} → 500.0
 *
 * <p>This is a utility class and cannot be instantiated.
 *
 * @author QuickEconomy
 * @since 1.3
 */
public class AbbreviationUtil {

    /**
     * Parses a string amount that may end with an abbreviation suffix into a {@code double}.
     * <p>
     * If the string ends with one of the recognised suffix characters ({@code K}, {@code M},
     * {@code B}, {@code T}), the numeric portion is multiplied by the corresponding factor.
     * Plain numbers without a suffix are returned as-is.
     *
     * @param amount the string to parse – must not be {@code null} or empty
     * @return the parsed monetary value, always &gt; 0
     * @throws NumberFormatException    if the numeric portion cannot be parsed as a double
     * @throws IllegalArgumentException if the suffix character is unrecognised or the
     *                                  resulting value is not positive
     */
    public static double fromString(@NotNull String amount) throws NumberFormatException, IllegalArgumentException {
        if (amount == null || amount.isEmpty()) {
            throw new NumberFormatException("Input amount cannot be null or empty.");
        }

        amount = amount.trim().toLowerCase();
        char lastChar = amount.charAt(amount.length() - 1);
        String numberPart;
        double multiplier = 1.0;

        if (!Character.isDigit(lastChar)) {
            numberPart = amount.substring(0, amount.length() - 1);
            multiplier = switch (lastChar) {
                case 'k' -> 1_000;
                case 'm' -> 1_000_000;
                case 'b' -> 1_000_000_000;
                case 't' -> 1_000_000_000_000L;
                default -> throw new IllegalArgumentException("Invalid abbreviation: " + lastChar);
            };
        } else {
            numberPart = amount;
        }

        double value;
        try {
            value = Double.parseDouble(numberPart) * multiplier;
        } catch (NumberFormatException e) {
            throw new NumberFormatException("Invalid number format: " + numberPart);
        }

        if (value <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero.");
        }

        return TypeChecker.formatDouble(value);
    }

    /** Private constructor to prevent instantiation. */
    private AbbreviationUtil() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}
