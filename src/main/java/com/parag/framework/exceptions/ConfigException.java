package com.parag.framework.exceptions;

/** Missing or invalid configuration (file, key or value). */
public class ConfigException extends FrameworkException {

    public ConfigException(String message) {
        super(message);
    }

    public ConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
