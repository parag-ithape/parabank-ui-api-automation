package com.parag.framework.driver;

import com.parag.framework.config.ConfigKeys;
import com.parag.framework.config.ConfigManager;
import com.parag.framework.exceptions.FrameworkException;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.MutableCapabilities;
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

/**
 * Creates fully configured WebDriver instances. This is the ONLY class that calls "new XxxDriver".
 * Selenium Manager (built into Selenium 4.6+) resolves driver binaries automatically,
 * so no driver .exe files are committed to the repo.
 */
public final class DriverFactory {

    private DriverFactory() { }

    public static WebDriver create(BrowserType browser) {
        ExecutionMode mode = ExecutionMode.from(ConfigManager.get(ConfigKeys.EXECUTION_MODE));
        MutableCapabilities options = BrowserOptionsFactory.create(browser);

        WebDriver driver = switch (mode) {
            case LOCAL -> createLocal(browser, options);
            case GRID -> createRemote(ConfigManager.get(ConfigKeys.GRID_URL), options);
        };

        // Timeouts. Note: NO implicit wait (explicit waits only, see WaitUtils).
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(ConfigManager.getInt(ConfigKeys.TIMEOUT_PAGELOAD)));
        driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(ConfigManager.getInt(ConfigKeys.TIMEOUT_SCRIPT)));
        return driver;
    }

    private static WebDriver createLocal(BrowserType browser, Capabilities options) {
        return switch (browser) {
            case CHROME -> new ChromeDriver((ChromeOptions) options);
            case FIREFOX -> new FirefoxDriver((FirefoxOptions) options);
            case EDGE -> new EdgeDriver((EdgeOptions) options);
        };
    }

    private static WebDriver createRemote(String url, Capabilities options) {
        try {
            return new RemoteWebDriver(URI.create(url).toURL(), options);
        } catch (MalformedURLException | IllegalArgumentException e) {
            throw new FrameworkException("Invalid remote WebDriver URL: " + url, e);
        }
    }
}
