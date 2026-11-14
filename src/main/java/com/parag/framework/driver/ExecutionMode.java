package com.parag.framework.driver;

import com.parag.framework.exceptions.ConfigException;

import java.util.Locale;

/** Where the browser runs: on this machine or on a Selenium Grid. */
public enum ExecutionMode {
    LOCAL, GRID;

    public static ExecutionMode from(String value) {
        try {
            return ExecutionMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ConfigException("Unsupported execution.mode: '" + value + "'. Use local|grid", e);
        }
    }
}
