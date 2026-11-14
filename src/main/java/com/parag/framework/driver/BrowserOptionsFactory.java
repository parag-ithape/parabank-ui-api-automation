package com.parag.framework.driver;

import com.parag.framework.config.ConfigKeys;
import com.parag.framework.config.ConfigManager;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxOptions;

/**
 * Builds browser-specific options in ONE place. The same options are used locally and on a Grid,
 * so behaviour is identical everywhere. Browser-specific flags never appear in pages.
 */
public final class BrowserOptionsFactory {

    private BrowserOptionsFactory() { }

    public static MutableCapabilities create(BrowserType browser) {
        boolean headless = ConfigManager.getBoolean(ConfigKeys.HEADLESS);
        String width = ConfigManager.get(ConfigKeys.WINDOW_WIDTH);
        String height = ConfigManager.get(ConfigKeys.WINDOW_HEIGHT);
        PageLoadStrategy pls = PageLoadStrategy.fromString(ConfigManager.get(ConfigKeys.PAGE_LOAD_STRATEGY));

        return switch (browser) {
            case CHROME -> {
                ChromeOptions o = new ChromeOptions();
                if (headless) {
                    o.addArguments("--headless=new");                   // new headless = same engine as headed
                }
                o.addArguments("--window-size=" + width + "," + height, "--disable-notifications");
                o.setPageLoadStrategy(pls);
                yield o;
            }
            case EDGE -> {
                EdgeOptions o = new EdgeOptions();                       // Chromium based, same flags as Chrome
                if (headless) {
                    o.addArguments("--headless=new");
                }
                o.addArguments("--window-size=" + width + "," + height);
                o.setPageLoadStrategy(pls);
                yield o;
            }
            case FIREFOX -> {
                FirefoxOptions o = new FirefoxOptions();
                if (headless) {
                    o.addArguments("-headless");
                }
                o.addArguments("--width=" + width, "--height=" + height);
                o.setPageLoadStrategy(pls);
                yield o;
            }
        };
    }
}
