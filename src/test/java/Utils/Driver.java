package Utils;

import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.remote.RemoteWebDriver;

import java.net.MalformedURLException;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;

/**
 * Starts, gives out and stops the browser. One browser PER THREAD, so parallel scenarios never
 * click in each other's browser. Selenium Manager downloads the right driver automatically.
 *
 * <pre>
 *   Driver.start();          // in the @Before hook of @ui scenarios
 *   Driver.get();            // in pages
 *   Driver.quit();           // in the @After hook, always
 * </pre>
 */
public final class Driver {

    /** Supported browsers, chosen with -Dbrowser=chrome|firefox|edge. */
    public enum Browser {
        CHROME, FIREFOX, EDGE;

        public static Browser from(String value) {
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException | NullPointerException e) {
                throw new FrameworkException("Unsupported browser: '" + value + "'. Use chrome|firefox|edge", e);
            }
        }
    }

    private static final ThreadLocal<WebDriver> CURRENT = new ThreadLocal<>();

    private Driver() { }

    /** Starts the browser from config (browser, headless, window size, local or grid). */
    public static void start() {
        start(Browser.from(Config.get(Config.BROWSER)));
    }

    public static void start(Browser browser) {
        MutableCapabilities options = options(browser);
        String mode = Config.get(Config.EXECUTION_MODE).toLowerCase(Locale.ROOT);
        WebDriver driver = switch (mode) {
            case "local" -> local(browser, options);
            case "grid" -> remote(Config.get(Config.GRID_URL), options);
            default -> throw new FrameworkException("Unsupported execution.mode: '" + mode + "'. Use local|grid");
        };
        // NO implicit wait on purpose: all waiting is explicit, in Actions.
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(Config.getInt(Config.TIMEOUT_PAGELOAD)));
        driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(Config.getInt(Config.TIMEOUT_SCRIPT)));
        CURRENT.set(driver);
    }

    public static WebDriver get() {
        WebDriver driver = CURRENT.get();
        if (driver == null) {
            throw new IllegalStateException("No browser on thread " + Thread.currentThread().getName()
                    + ". Is the scenario tagged @ui? Is the page created inside a step?");
        }
        return driver;
    }

    public static boolean isStarted() {
        return CURRENT.get() != null;
    }

    /** Closes the browser and frees the thread (no dead browser left for the next scenario). */
    public static void quit() {
        WebDriver driver = CURRENT.get();
        if (driver != null) {
            try {
                driver.quit();
            } finally {
                CURRENT.remove();
            }
        }
    }

    // ---------- Browser options: the ONLY place with browser-specific flags ----------
    private static MutableCapabilities options(Browser browser) {
        boolean headless = Config.getBoolean(Config.HEADLESS);
        String width = Config.get(Config.WINDOW_WIDTH);
        String height = Config.get(Config.WINDOW_HEIGHT);
        PageLoadStrategy pageLoad = PageLoadStrategy.fromString(Config.get(Config.PAGE_LOAD_STRATEGY));

        return switch (browser) {
            case CHROME -> {
                ChromeOptions o = new ChromeOptions();
                if (headless) {
                    o.addArguments("--headless=new");
                }
                o.addArguments("--window-size=" + width + "," + height, "--disable-notifications");
                o.setPageLoadStrategy(pageLoad);
                yield o;
            }
            case EDGE -> {
                EdgeOptions o = new EdgeOptions();
                if (headless) {
                    o.addArguments("--headless=new");
                }
                o.addArguments("--window-size=" + width + "," + height);
                o.setPageLoadStrategy(pageLoad);
                yield o;
            }
            case FIREFOX -> {
                FirefoxOptions o = new FirefoxOptions();
                if (headless) {
                    o.addArguments("-headless");
                }
                o.addArguments("--width=" + width, "--height=" + height);
                o.setPageLoadStrategy(pageLoad);
                yield o;
            }
        };
    }

    private static WebDriver local(Browser browser, MutableCapabilities options) {
        return switch (browser) {
            case CHROME -> new ChromeDriver((ChromeOptions) options);
            case FIREFOX -> new FirefoxDriver((FirefoxOptions) options);
            case EDGE -> new EdgeDriver((EdgeOptions) options);
        };
    }

    private static WebDriver remote(String url, MutableCapabilities options) {
        try {
            return new RemoteWebDriver(URI.create(url).toURL(), options);
        } catch (MalformedURLException | IllegalArgumentException e) {
            throw new FrameworkException("Invalid grid.url: " + url, e);
        }
    }
}
