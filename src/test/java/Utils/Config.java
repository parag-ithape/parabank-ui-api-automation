package Utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Properties;

/**
 * All settings, read from config.properties and env-&lt;env&gt;.properties.
 *
 * <p>Where a value comes from (first one found wins):</p>
 * <ol>
 *   <li>Command line: {@code -Dbrowser=firefox}</li>
 *   <li>Environment variable: {@code BROWSER=firefox} (upper case, '.' becomes '_')</li>
 *   <li>{@code env-<env>.properties}, chosen with {@code -Denv=local} (default: public)</li>
 *   <li>{@code config.properties}</li>
 * </ol>
 * A key found nowhere stops the run with a clear message.
 */
public final class Config {

    // ---------- Keys (use these instead of typing the key text) ----------
    public static final String BASE_URL = "baseUrl";
    public static final String BROWSER = "browser";
    public static final String EXECUTION_MODE = "execution.mode";
    public static final String HEADLESS = "headless";
    public static final String WINDOW_WIDTH = "window.width";
    public static final String WINDOW_HEIGHT = "window.height";
    public static final String GRID_URL = "grid.url";
    public static final String PAGE_LOAD_STRATEGY = "page.load.strategy";
    public static final String TIMEOUT_EXPLICIT = "timeout.explicit";
    public static final String TIMEOUT_PAGELOAD = "timeout.pageload";
    public static final String TIMEOUT_SCRIPT = "timeout.script";
    public static final String TIMEOUT_POLLING_MILLIS = "timeout.polling.millis";
    public static final String CLICK_RETRY_COUNT = "click.retry.count";
    public static final String API_BASE_PATH = "api.base.path";
    public static final String API_TIMEOUT_SECONDS = "api.timeout.seconds";
    public static final String API_MAX_RESPONSE_MILLIS = "api.max.response.millis";
    public static final String SCREENSHOT_ON_FAILURE = "screenshot.on.failure";
    public static final String ATTACH_PAGE_SOURCE_ON_FAILURE = "attach.page.source.on.failure";
    public static final String MESSAGES_LOCALE = "messages.locale";

    private static final String DEFAULT_ENV = "public";
    private static final Properties PROPS = load();

    private Config() { }

    /** Active environment: public (default) or local. Set with -Denv=local or the ENV variable. */
    public static String env() {
        String env = System.getProperty("env", System.getenv("ENV"));
        return (env == null || env.isBlank()) ? DEFAULT_ENV : env.trim().toLowerCase(Locale.ROOT);
    }

    public static String get(String key) {
        String fromCommandLine = System.getProperty(key);
        if (fromCommandLine != null && !fromCommandLine.isBlank()) {
            return fromCommandLine.trim();
        }
        String fromEnvironment = System.getenv(key.toUpperCase(Locale.ROOT).replace('.', '_'));
        if (fromEnvironment != null && !fromEnvironment.isBlank()) {
            return fromEnvironment.trim();
        }
        String fromFile = PROPS.getProperty(key);
        if (fromFile == null) {
            throw new FrameworkException("Missing config key: " + key + " (env=" + env() + ")");
        }
        return fromFile.trim();
    }

    public static String get(String key, String defaultValue) {
        try {
            return get(key);
        } catch (FrameworkException e) {
            return defaultValue;
        }
    }

    public static int getInt(String key) {
        String value = get(key);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new FrameworkException("Config key " + key + " must be a whole number but was '" + value + "'", e);
        }
    }

    public static boolean getBoolean(String key) {
        return Boolean.parseBoolean(get(key));
    }

    // Loaded once, read-only afterwards, so safe for parallel threads. Classpath loading works on any OS.
    private static Properties load() {
        Properties p = new Properties();
        loadInto(p, "config.properties");
        loadInto(p, "env-" + env() + ".properties");
        return p;
    }

    private static void loadInto(Properties target, String resource) {
        try (InputStream in = Config.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new FrameworkException("Config file not found: " + resource);
            }
            target.load(in);
        } catch (IOException e) {
            throw new FrameworkException("Cannot read config file: " + resource, e);
        }
    }
}
