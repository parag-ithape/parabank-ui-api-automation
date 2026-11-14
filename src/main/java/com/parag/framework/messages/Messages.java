package com.parag.framework.messages;

import com.parag.framework.config.ConfigKeys;
import com.parag.framework.config.ConfigManager;
import com.parag.framework.exceptions.FrameworkException;
import com.parag.framework.utils.PlaceholderUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Expected UI messages from {@code messages/messages_<locale>.properties}. The locale comes from config
 * (messages.locale). Files are read as UTF-8 so accented characters work. Read-only, so thread-safe.
 */
public final class Messages {

    private static final Properties MESSAGES = load(ConfigManager.get(ConfigKeys.MESSAGES_LOCALE, "en"));

    private Messages() { }

    private static Properties load(String locale) {
        String resource = "messages/messages_" + locale + ".properties";
        try (InputStream in = Messages.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new FrameworkException("Messages file not found: " + resource);
            }
            Properties p = new Properties();
            p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            return p;
        } catch (IOException e) {
            throw new FrameworkException("Cannot load messages: " + resource, e);
        }
    }

    /** Messages.get("register.success.title", "bdd_user_1731") */
    public static String get(String key, Object... args) {
        String msg = MESSAGES.getProperty(key);
        if (msg == null) {
            throw new FrameworkException("Missing message key: " + key);
        }
        return PlaceholderUtils.fill(msg, args);
    }
}
