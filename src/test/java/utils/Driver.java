package utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Starts, gives out and stops the browser.
 *
 * <pre>
 *   Driver.open();          // @Before hook of @ui scenarios: reuses the open browser or starts one
 *   Driver.get();           // pages get the browser
 *   Driver.release();       // @After hook: clears the session (browser.reuse=true) or closes the browser
 *   Driver.quitAll();       // @AfterAll hook: closes every browser at the end of the run
 * </pre>
 *
 * <p><b>browser.reuse=true</b> (default): ONE browser for the whole run. Between scenarios its cookies and
 * storage are cleared, so every scenario still starts logged out. If the browser crashed, a new one starts.</p>
 *
 * <p>The browser is kept PER THREAD. With the default single thread that is one browser; with -DdpThreads=N
 * each parallel thread gets its own, so parallel scenarios never click in each other's browser.
 * Selenium Manager downloads the right driver automatically.</p>
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

        String displayName() {
            String lower = name().toLowerCase(Locale.ROOT);
            return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
        }
    }

    private static final Logger LOG = LogManager.getLogger(Driver.class);
    private static final ThreadLocal<WebDriver> CURRENT = new ThreadLocal<>();
    /** Every open browser on every thread, so quitAll() can close them all at the end. */
    private static final Set<WebDriver> ALL_OPEN = ConcurrentHashMap.newKeySet();
    /**
     * Selenium warns through java.util.logging when Chrome is newer than its DevTools (CDP) support:
     * "Unable to find an exact match for CDP version". Harmless here (no CDP features are used), so it is
     * hidden. Kept in a field because java.util.logging forgets settings on loggers nobody references.
     */
    private static final java.util.logging.Logger CDP_LOGGER = java.util.logging.Logger.getLogger("org.openqa.selenium.devtools");

    static {
        CDP_LOGGER.setLevel(java.util.logging.Level.SEVERE);
        // Safety net: browsers never stay open after the JVM ends, even if the run was interrupted.
        Runtime.getRuntime().addShutdownHook(new Thread(Driver::quitAll, "close-browsers"));
    }

    private Driver() { }

    /** Before a @ui scenario: reuses this thread's browser if it is still alive, otherwise starts one. */
    public static void open() {
        WebDriver existing = CURRENT.get();
        if (existing != null && Config.getBoolean(Config.BROWSER_REUSE)) {
            if (isAlive(existing)) {
                LOG.info("Using the browser that is already open");
                return;
            }
            LOG.warn("The open browser stopped responding; starting a new one");
            quit();
        }
        start(Browser.from(Config.get(Config.BROWSER)));
    }

    /** After a @ui scenario: clears the session for the next scenario, or closes the browser. */
    public static void release() {
        if (!isStarted()) {
            return;
        }
        if (!Config.getBoolean(Config.BROWSER_REUSE)) {
            quit();
            return;
        }
        try {
            clearSession();
        } catch (WebDriverException e) {
            LOG.warn("Could not clear the browser session ({}); closing the browser instead", e.getClass().getSimpleName());
            quit();
        }
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

    /** Closes this thread's browser. */
    public static void quit() {
        WebDriver driver = CURRENT.get();
        if (driver != null) {
            try {
                close(driver);
            } finally {
                CURRENT.remove();
            }
        }
    }

    /** Closes every browser still open (end of the run). */
    public static void quitAll() {
        for (WebDriver driver : new ArrayList<>(ALL_OPEN)) {
            close(driver);
        }
        CURRENT.remove();
    }

    // ------------------------------------------------------------------------------------------------

    private static void start(Browser browser) {
        boolean headless = Config.getBoolean(Config.HEADLESS);
        LOG.info("Starting {} ({}, window {}x{})", browser.displayName(), headless ? "headless" : "visible",
                Config.get(Config.WINDOW_WIDTH), Config.get(Config.WINDOW_HEIGHT));
        MutableCapabilities options = options(browser, headless);
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
        ALL_OPEN.add(driver);
    }

    /** Logs out ParaBank by removing its cookies and storage, and closes extra tabs. */
    private static void clearSession() {
        WebDriver driver = get();
        List<String> handles = new ArrayList<>(driver.getWindowHandles());
        for (int i = 1; i < handles.size(); i++) {
            driver.switchTo().window(handles.get(i)).close();
        }
        driver.switchTo().window(handles.get(0));
        driver.manage().deleteAllCookies();
        ((JavascriptExecutor) driver).executeScript(
                "try { window.localStorage.clear(); window.sessionStorage.clear(); } catch (e) { }");
        LOG.info("Browser session cleared for the next scenario");
    }

    private static boolean isAlive(WebDriver driver) {
        try {
            driver.getWindowHandle();
            return true;
        } catch (WebDriverException e) {
            return false;
        }
    }

    private static void close(WebDriver driver) {
        try {
            driver.quit();
            LOG.info("Browser closed");
        } catch (WebDriverException e) {
            LOG.debug("Browser was already closed: {}", e.getMessage());
        } finally {
            ALL_OPEN.remove(driver);
        }
    }

    // ---------- Browser options: the ONLY place with browser-specific flags ----------
    private static MutableCapabilities options(Browser browser, boolean headless) {
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
