package com.parag.framework.driver;

import com.parag.framework.exceptions.ConfigException;

import java.util.Locale;

/** Supported browsers. from() gives a clear error for typos such as "chorme". */
public enum BrowserType {
    CHROME, FIREFOX, EDGE;

    public static BrowserType from(String value) {
        try {
            return BrowserType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ConfigException("Unsupported browser: '" + value + "'. Use chrome|firefox|edge", e);
        }
    }
}
