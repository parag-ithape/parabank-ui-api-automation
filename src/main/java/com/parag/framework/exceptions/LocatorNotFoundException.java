package com.parag.framework.exceptions;

/** Missing or invalid locator file, key, format or strategy. */
public class LocatorNotFoundException extends FrameworkException {

    public LocatorNotFoundException(String message) {
        super(message);
    }

    public LocatorNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
