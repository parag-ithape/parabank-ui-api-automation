package utils;

import org.openqa.selenium.By;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebElement;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
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
        By selector = switch (strategy.toLowerCase(Locale.ROOT)) {
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
        return new Named(describe(key, args), selector);
    }

    public String page() {
        return page;
    }

    /** All keys in the file. Used by LocatorIntegrityTest. */
    public Set<String> keys() {
        return entries.stringPropertyNames();
    }

    /** Plain-language name of an element in logs. Falls back to the technical selector for other locators. */
    public static String nameOf(By by) {
        return by instanceof Named named ? named.name : by.toString();
    }

    /**
     * Turns a key into words a reader understands:
     * "firstName.input" on page "register" → "first name field (Register page)";
     * "table.row.byText" with "Savings" → "table row "Savings" (Common)".
     */
    private String describe(String key, Object... args) {
        List<String> words = new ArrayList<>();
        for (String part : key.split("\\.")) {
            if (part.startsWith("by") && part.length() > 2 && Character.isUpperCase(part.charAt(2))) {
                continue;                                            // "byText", "byAccountId": not a word for readers
            }
            words.add(switch (part) {
                case "input" -> "field";
                case "select" -> "dropdown";
                default -> part.replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase(Locale.ROOT);
            });
        }
        StringBuilder name = new StringBuilder(String.join(" ", words));
        if (args != null) {
            for (Object arg : args) {
                name.append(" \"").append(arg).append('"');
            }
        }
        return name + " (" + pageLabel() + ")";
    }

    private String pageLabel() {
        StringBuilder label = new StringBuilder();
        for (String word : page.split("-")) {
            label.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(' ');
        }
        String title = label.toString().trim();
        return title.endsWith("Menu") || title.equals("Common") ? title : title + " page";
    }

    /**
     * A By with a readable name. It finds elements exactly like the selector it wraps; only toString()
     * differs, so timeouts read "waiting for visibility of login button (Login page) [By.cssSelector: …]".
     */
    static final class Named extends By {
        private final String name;
        private final By selector;

        Named(String name, By selector) {
            this.name = name;
            this.selector = selector;
        }

        @Override
        public WebElement findElement(SearchContext context) {
            return context.findElement(selector);
        }

        @Override
        public List<WebElement> findElements(SearchContext context) {
            return context.findElements(selector);
        }

        @Override
        public String toString() {
            return name + " [" + selector + "]";
        }
    }
}
