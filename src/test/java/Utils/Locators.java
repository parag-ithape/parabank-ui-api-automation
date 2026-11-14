package Utils;

import org.openqa.selenium.By;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads the locators of one page from locators/&lt;page&gt;.properties and turns them into Selenium By objects.
 *
 * <pre>
 *   Locators login = Locators.forPage("login");        // reads locators/login.properties
 *   By button = login.by("login.button");               // line: login.button=css:input[value='Log In']
 *   By row = common.by("table.row.byText", "Savings");  // fills {0} in dynamic locators
 * </pre>
 * Each file is read once and shared read-only by all threads.
 */
public final class Locators {

    private static final Map<String, Locators> CACHE = new ConcurrentHashMap<>();

    private final String page;
    private final Properties entries = new Properties();

    private Locators(String page) {
        this.page = page;
        String resource = "locators/" + page + ".properties";
        try (InputStream in = Locators.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new FrameworkException("Locator file not found: " + resource);
            }
            entries.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new FrameworkException("Cannot read locator file: " + resource, e);
        }
    }

    public static Locators forPage(String page) {
        return CACHE.computeIfAbsent(page, Locators::new);
    }

    /** Finds the key in the page file and converts "strategy:value" to a By. Args fill {0}, {1}… */
    public By by(String key, Object... args) {
        String raw = entries.getProperty(key);
        if (raw == null) {
            throw new FrameworkException("Missing locator key: " + page + "." + key);
        }
        String resolved = Messages.fill(raw, args);
        int sep = resolved.indexOf(':');                  // first ':' only, XPath may contain more
        if (sep < 1) {
            throw new FrameworkException("Bad locator format for " + page + "." + key + " -> " + resolved
                    + " (expected strategy:value)");
        }
        String strategy = resolved.substring(0, sep).trim();
        String value = resolved.substring(sep + 1).trim();
        if (value.isEmpty()) {
            throw new FrameworkException("Empty locator value for " + page + "." + key);
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
            default -> throw new FrameworkException("Unknown strategy '" + strategy + "' in " + page + "." + key);
        };
    }

    public String page() {
        return page;
    }

    /** All keys in the file. Used by LocatorIntegrityTest. */
    public Set<String> keys() {
        return entries.stringPropertyNames();
    }
}
