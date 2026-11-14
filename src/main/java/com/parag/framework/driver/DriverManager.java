package com.parag.framework.driver;

import org.openqa.selenium.WebDriver;

/**
 * Holds one WebDriver PER THREAD.
 *
 * <p>Why ThreadLocal: scenarios run in parallel on a thread pool. A static WebDriver field would be
 * shared by all threads, so scenario A would click in the browser of scenario B. ThreadLocal gives every
 * thread its own browser.</p>
 *
 * <p>Always call quit() in the @After hook. It also calls remove() so pooled threads do not keep a
 * dead driver (memory leak / "session not found" errors).</p>
 */
public final class DriverManager {

    private static final ThreadLocal<WebDriver> DRIVER = new ThreadLocal<>();

    private DriverManager() { }

    public static WebDriver getDriver() {
        WebDriver driver = DRIVER.get();
        if (driver == null) {
            throw new IllegalStateException("WebDriver not initialised for thread " + Thread.currentThread().getName()
                    + ". Is the scenario tagged @ui? Is the page created inside a step?");
        }
        return driver;
    }

    public static void setDriver(WebDriver driver) {
        DRIVER.set(driver);
    }

    public static boolean hasDriver() {
        return DRIVER.get() != null;
    }

    public static void quit() {
        WebDriver driver = DRIVER.get();
        if (driver != null) {
            try {
                driver.quit();
            } finally {
                DRIVER.remove();
            }
        }
    }
}
