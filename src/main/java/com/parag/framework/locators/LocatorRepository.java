package com.parag.framework.locators;

import com.parag.framework.exceptions.LocatorNotFoundException;
import com.parag.framework.utils.PlaceholderUtils;
import org.openqa.selenium.By;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

/**
 * Holds the locators of ONE page file ({@code locators/<page>.properties}) and converts
 * {@code strategy:value} strings to Selenium By objects.
 *
 * <p>Read-only after construction, so one instance is safely shared by all threads (see {@link Locators}).</p>
 */
public final class LocatorRepository {

    private final String pageName;
    private final Properties entries;

    LocatorRepository(String pageName) {
        this.pageName = pageName;
        this.entries = new Properties();
        String resource = "locators/" + pageName + ".properties";
        try (InputStream in = LocatorRepository.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new LocatorNotFoundException("Locator file not found: " + resource);
            }
            entries.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new LocatorNotFoundException("Cannot read locator file: " + resource, e);
        }
    }

    /**
     * @param key  key inside the page file, e.g. "login.button"
     * @param args values for {0},{1}… placeholders (for dynamic locators)
     */
    public By by(String key, Object... args) {
        String raw = entries.getProperty(key);
        if (raw == null) {
            throw new LocatorNotFoundException("Missing locator key: " + pageName + "." + key);
        }
        // PlaceholderUtils, NOT MessageFormat: MessageFormat treats single quotes as escape characters
        // and would silently break XPaths such as //td[text()='{0}'].
        return toBy(key, PlaceholderUtils.fill(raw, args));
    }

    private By toBy(String key, String raw) {
        int sep = raw.indexOf(':');                       // split on the FIRST ':' only (XPath may contain ':')
        if (sep < 1) {
            throw new LocatorNotFoundException("Bad locator format for " + pageName + "." + key + " -> " + raw
                    + " (expected strategy:value)");
        }
        String strategy = raw.substring(0, sep).trim();
        String value = raw.substring(sep + 1).trim();
        if (value.isEmpty()) {
            throw new LocatorNotFoundException("Empty locator value for " + pageName + "." + key);
        }
        return switch (strategy.toLowerCase(Locale.ROOT)) {
            case "id" -> By.id(value);
            case "name" -> By.name(value);
            case "css" -> By.cssSelector(value);
            case "xpath" -> By.xpath(value);
            case "linktext" -> By.linkText(value);
            case "partiallinktext" -> By.partialLinkText(value);
            case "classname" -> By.className(value);
            case "tagname" -> By.tagName(value);
            default -> throw new LocatorNotFoundException("Unknown strategy '" + strategy + "' in " + pageName + "." + key);
        };
    }

    public String pageName() {
        return pageName;
    }

    /** Used by LocatorIntegrityTest to validate every entry without a browser. */
    public Set<String> keys() {
        return entries.stringPropertyNames();
    }
}
