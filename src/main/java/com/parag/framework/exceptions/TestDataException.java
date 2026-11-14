package com.parag.framework.exceptions;

/** Missing or invalid test data file, or an unset secret placeholder. */
public class TestDataException extends FrameworkException {

    public TestDataException(String message) {
        super(message);
    }

    public TestDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
