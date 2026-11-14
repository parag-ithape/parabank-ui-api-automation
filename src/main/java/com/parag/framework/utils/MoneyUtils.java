package com.parag.framework.utils;

import com.parag.framework.exceptions.FrameworkException;

import java.math.BigDecimal;

/** Bank amounts are always BigDecimal: never double, which loses cents to rounding. */
public final class MoneyUtils {

    private MoneyUtils() { }

    /** "$1,234.50" → 1234.50, "-$20.00" → -20.00. Throws FrameworkException with the raw text if it is not money. */
    public static BigDecimal parse(String uiText) {
        if (uiText == null || uiText.isBlank()) {
            throw new FrameworkException("Cannot parse money from empty text");
        }
        String cleaned = uiText.replace("$", "").replace(",", "").trim();
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            throw new FrameworkException("Cannot parse money from '" + uiText + "'", e);
        }
    }
}
