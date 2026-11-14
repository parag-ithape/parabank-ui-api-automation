package utils;

import java.math.BigDecimal;

/** Bank amounts are always BigDecimal: never double, which loses cents to rounding. */
public final class Money {

    private Money() { }

    /** "$1,234.50" → 1234.50, "-$20.00" → -20.00 */
    public static BigDecimal parse(String uiText) {
        if (uiText == null || uiText.isBlank()) {
            throw new FrameworkException("Cannot read an amount from empty text");
        }
        String cleaned = uiText.replace("$", "").replace(",", "").trim();
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            throw new FrameworkException("Cannot read an amount from '" + uiText + "'", e);
        }
    }
}
