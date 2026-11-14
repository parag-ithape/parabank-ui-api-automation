package Utils;

/**
 * The one error type of the framework: missing config key, bad locator, missing test data, unset secret.
 * Unchecked, so methods don't need "throws Exception". The message always says what is wrong and where.
 */
public class FrameworkException extends RuntimeException {

    public FrameworkException(String message) {
        super(message);
    }

    public FrameworkException(String message, Throwable cause) {
        super(message, cause);
    }
}
