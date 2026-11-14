package utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Expected UI texts from messages_&lt;locale&gt;.properties (locale from config, default en).
 * Steps compare the screen against these, so a wording change in the app is a one-line fix.
 *
 * <pre>
 *   Messages.get("login.error.invalid")
 *   Messages.get("register.success.title", username)    // fills {0}
 * </pre>
 */
public final class Messages {

    private static final Properties TEXTS = load(Config.get(Config.MESSAGES_LOCALE, "en"));

    private Messages() { }

    public static String get(String key, Object... args) {
        String text = TEXTS.getProperty(key);
        if (text == null) {
            throw new FrameworkException("Missing message key: " + key);
        }
        return fill(text, args);
    }

    /**
     * Replaces {0}, {1}… literally. Not MessageFormat: that treats single quotes as escapes and would
     * break XPaths like //td[text()='{0}'] and texts with apostrophes.
     */
    public static String fill(String template, Object... args) {
        String result = template;
        for (int i = 0; args != null && i < args.length; i++) {
            result = result.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return result;
    }

    private static Properties load(String locale) {
        String resource = "messages_" + locale + ".properties";
        try (InputStream in = Messages.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new FrameworkException("Messages file not found: " + resource);
            }
            Properties p = new Properties();
            p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            return p;
        } catch (IOException e) {
            throw new FrameworkException("Cannot read messages file: " + resource, e);
        }
    }
}
