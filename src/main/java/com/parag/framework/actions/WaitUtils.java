package com.parag.framework.actions;

import com.parag.framework.config.ConfigKeys;
import com.parag.framework.config.ConfigManager;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;

/**
 * ALL synchronisation goes through this class. Rules:
 * <ul>
 *   <li>No implicit waits anywhere (Selenium docs: mixing them gives unpredictable wait times).</li>
 *   <li>No Thread.sleep(): slow AND flaky.</li>
 *   <li>Polls every timeout.polling.millis and ignores stale elements while waiting.</li>
 * </ul>
 * One instance per page object (per scenario thread), never shared across threads.
 */
public class WaitUtils {

    private final WebDriver driver;
    private final Duration timeout;
    private final Duration polling;
    private final WebDriverWait wait;

    public WaitUtils(WebDriver driver) {
        this.driver = driver;
        this.timeout = Duration.ofSeconds(ConfigManager.getInt(ConfigKeys.TIMEOUT_EXPLICIT));
        this.polling = Duration.ofMillis(ConfigManager.getInt(ConfigKeys.TIMEOUT_POLLING_MILLIS));
        this.wait = newWait();
    }

    private WebDriverWait newWait() {
        WebDriverWait w = new WebDriverWait(driver, timeout, polling);
        w.ignoring(StaleElementReferenceException.class);
        return w;
    }

    public WebElement visible(By by) {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(by));
    }

    public WebElement clickable(By by) {
        return wait.until(ExpectedConditions.elementToBeClickable(by));
    }

    public WebElement present(By by) {
        return wait.until(ExpectedConditions.presenceOfElementLocated(by));
    }

    public List<WebElement> allVisible(By by) {
        return wait.until(ExpectedConditions.visibilityOfAllElementsLocatedBy(by));
    }

    public boolean invisible(By by) {
        return wait.until(ExpectedConditions.invisibilityOfElementLocated(by));
    }

    public boolean textPresent(By by, String text) {
        return wait.until(ExpectedConditions.textToBePresentInElementLocated(by, text));
    }

    public boolean urlContains(String part) {
        return wait.until(ExpectedConditions.urlContains(part));
    }

    /** ParaBank fills some dropdowns (account lists) after the page loads: wait until options exist. */
    public void optionsLoaded(By select) {
        until(d -> !new Select(d.findElement(select)).getOptions().isEmpty(), "Options loaded in " + select);
    }

    /** Waits for document.readyState == complete. */
    public void pageReady() {
        until(d -> "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState")),
                "document.readyState == complete");
    }

    /**
     * Custom condition with a readable timeout message. Uses a fresh wait, because withMessage() mutates
     * the wait it is called on and the message would otherwise stick to every later timeout.
     */
    public <T> T until(Function<WebDriver, T> condition, String description) {
        WebDriverWait w = newWait();
        w.withMessage(description);
        return w.until(condition);
    }
}
