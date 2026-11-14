package com.parag.framework.exceptions;

/** Base of all framework errors. Unchecked, so method signatures stay clean (no "throws Exception" everywhere). */
public class FrameworkException extends RuntimeException {

    public FrameworkException(String message) {
        super(message);
    }

    public FrameworkException(String message, Throwable cause) {
        super(message, cause);
    }
}
