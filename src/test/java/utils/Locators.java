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
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads ALL locators from the single file locators.properties and turns them into Selenium By objects.
 * Each line starts with its page: "login.login.button=css:input[value='Log In']" belongs to page "login".
 *
 * <pre>
 *   Locators login = Locators.forPage("login");        // the "login.*" section of locators.properties
 *   By button = login.by("login.button");               // line: login.login.button=css:input[value='Log In']
 *   By row = common.by("table.row.byText", "Savings");  // fills {0} in dynamic locators
 * </pre>
 * The file is read once and shared read-only by all threads.
 */
public final class Locators {

    /** The one locator file, at the root of src/test/resources. */
    public static final String FILE = "locators.properties";

    private static final Properties ALL = load();
    private static final Map<String, Locators> CACHE = new ConcurrentHashMap<>();

    private final String page;
    private final Properties entries = new Properties();

    private Locators(String page) {
        this.page = page;
        String prefix = page + ".";
        for (String key : ALL.stringPropertyNames()) {
            if (key.startsWith(prefix)) {
                entries.setProperty(key.substring(prefix.length()), ALL.getProperty(key));
            }
        }
        if (entries.isEmpty()) {
            throw new FrameworkException("No locators for page \"" + page + "\" in " + FILE
                    + " (expected lines starting with \"" + prefix + "\")");
        }
    }

    public static Locators forPage(String page) {
        return CACHE.computeIfAbsent(page, Locators::new);
    }

    /** Every page that has a section in locators.properties (the part of each key before the first '.'). */
    public static Set<String> pages() {
        Set<String> pages = new TreeSet<>();
        for (String key : ALL.stringPropertyNames()) {
            int dot = key.indexOf('.');
            pages.add(dot > 0 ? key.substring(0, dot) : key);
        }
        return pages;
    }

    /** Finds the key in the page's section and converts "strategy:value" to a By. Args fill {0}, {1}… */
    public By by(String key, Object... args) {
        String raw = entries.getProperty(key);
        if (raw == null) {
            throw new FrameworkException("Missing locator key: " + page + "." + key + " in " + FILE);
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

    /** All keys of this page's section (without the page prefix). Used by LocatorIntegrityTest. */
    public Set<String> keys() {
        return entries.stringPropertyNames();
    }

    private static Properties load() {
        try (InputStream in = Locators.class.getClassLoader().getResourceAsStream(FILE)) {
            if (in == null) {
                throw new FrameworkException("Locator file not found on the classpath: " + FILE);
            }
            Properties p = new Properties();
            p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            return p;
        } catch (IOException e) {
            throw new FrameworkException("Cannot read locator file: " + FILE, e);
        }
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
