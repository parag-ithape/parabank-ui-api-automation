package com.parag.framework.config;

import com.parag.framework.exceptions.ConfigException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Properties;

/**
 * Central, read-only access point for all configuration.
 *
 * <p>Resolution order (highest priority wins):</p>
 * <ol>
 *   <li>JVM system property ({@code -Dbrowser=firefox})</li>
 *   <li>Environment variable (key upper-cased, '.' → '_', e.g. {@code BROWSER})</li>
 *   <li>{@code config/env/<env>.properties}, env selected by {@code -Denv} / {@code ENV} (default public)</li>
 *   <li>{@code config/config.properties} defaults</li>
 * </ol>
 * <p>A key missing everywhere throws {@link ConfigException} (fail fast).</p>
 *
 * <p>Loads files from the CLASSPATH, not from file-system paths, so it works on Windows,
 * Linux CI runners and inside jars.</p>
 *
 * <p>Thread-safe: the Properties object is loaded once in a static initialiser and only read afterwards.</p>
 */
public final class ConfigManager {

    private static final String DEFAULT_ENV = "public";
    private static final String DEFAULT_FILE = "config/config.properties";
    private static final String ENV_FILE_PATTERN = "config/env/%s.properties";
    private static final Properties PROPS = load();

    private ConfigManager() { }

    /** Active environment name (public, local). Set with -Denv or the ENV variable. */
    public static String env() {
        String env = System.getProperty("env", System.getenv("ENV"));
        return (env == null || env.isBlank()) ? DEFAULT_ENV : env.trim().toLowerCase(Locale.ROOT);
    }

    private static Properties load() {
        Properties p = new Properties();
        loadInto(p, DEFAULT_FILE);                              // defaults first
        loadInto(p, String.format(ENV_FILE_PATTERN, env()));    // env file overrides defaults
        return p;
    }

    private static void loadInto(Properties target, String resource) {
        try (InputStream in = ConfigManager.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new ConfigException("Config file not found on classpath: " + resource);
            }
            target.load(in);
        } catch (IOException e) {
            throw new ConfigException("Unable to read config file: " + resource, e);
        }
    }

    /** Returns the resolved value or throws ConfigException if the key is missing everywhere. */
    public static String get(String key) {
        String sys = System.getProperty(key);
        if (sys != null && !sys.isBlank()) {
            return sys.trim();
        }

        String envVar = System.getenv(key.toUpperCase(Locale.ROOT).replace('.', '_'));
        if (envVar != null && !envVar.isBlank()) {
            return envVar.trim();
        }

        String fileValue = PROPS.getProperty(key);
        if (fileValue == null) {
            throw new ConfigException("Missing config key: " + key + " (env=" + env() + ")");
        }
        return fileValue.trim();
    }

    public static String get(String key, String defaultValue) {
        try {
            return get(key);
        } catch (ConfigException e) {
            return defaultValue;
        }
    }

    public static int getInt(String key) {
        String value = get(key);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new ConfigException("Config key " + key + " must be an integer but was '" + value + "'", e);
        }
    }

    public static boolean getBoolean(String key) {
        return Boolean.parseBoolean(get(key));
    }
}
