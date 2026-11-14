package utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.By;
import org.openqa.selenium.ElementClickInterceptedException;
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
 * Every browser interaction and every wait goes through here, so waiting, retries and logging live in
 * ONE place. Pages call these methods and never call driver.findElement(..).click() themselves.
 *
 * <p>Waiting rules: explicit waits only. No implicit waits, no Thread.sleep (slow AND flaky).
 * Polls every timeout.polling.millis and ignores stale elements while waiting.</p>
 *
 * <p>One instance per page object (so per scenario thread). Never share it between threads.</p>
 */
public class Actions {

    private static final Logger LOG = LogManager.getLogger(Actions.class);

    private final WebDriver driver;
    private final Duration timeout = Duration.ofSeconds(Config.getInt(Config.TIMEOUT_EXPLICIT));
    private final Duration polling = Duration.ofMillis(Config.getInt(Config.TIMEOUT_POLLING_MILLIS));
    private final int clickRetries = Config.getInt(Config.CLICK_RETRY_COUNT);
    private final WebDriverWait wait;

    public Actions(WebDriver driver) {
        this.driver = driver;
        this.wait = newWait();
    }

    // =============================== Interactions ===============================

    /** Waits until clickable, then clicks. Retries when the page re-renders or an overlay is in the way. */
    public void click(By by) {
        for (int attempt = 1; ; attempt++) {
            try {
                waitClickable(by).click();
                LOG.info("Click {}", Locators.nameOf(by));
                return;
            } catch (StaleElementReferenceException | ElementClickInterceptedException e) {
                if (attempt >= clickRetries) {
                    throw e;
                }
                LOG.warn("{} was not ready to click (the page was still changing), trying again ({} of {})",
                        Locators.nameOf(by), attempt, clickRetries);
            }
        }
    }

    /** Clears, then types. Use typeSecret() for passwords so the value is never logged. */
    public void type(By by, String text) {
        WebElement el = waitVisible(by);
        el.clear();
        el.sendKeys(text);
        if (text.isEmpty()) {
            LOG.info("Leave {} empty", Locators.nameOf(by));
        } else {
            LOG.info("Enter \"{}\" in {}", text, Locators.nameOf(by));
        }
    }

    public void typeSecret(By by, String secret) {
        WebElement el = waitVisible(by);
        el.clear();
        el.sendKeys(secret);
        if (secret.isEmpty()) {
            LOG.info("Leave {} empty", Locators.nameOf(by));
        } else {
            LOG.info("Enter ******** in {}", Locators.nameOf(by));
        }
    }

    public String text(By by) {
        return waitVisible(by).getText().trim();
    }

    /** HTML attribute as written in the page source. */
    public String attribute(By by, String name) {
        return waitPresent(by).getDomAttribute(name);
    }

    /** Current value of an input or dropdown, including what was typed. */
    public String value(By by) {
        return waitPresent(by).getDomProperty("value");
    }

    /** True/false check that never throws: false when the element is absent or hidden. */
    public boolean isDisplayed(By by) {
        try {
            return driver.findElements(by).stream().anyMatch(WebElement::isDisplayed);
        } catch (StaleElementReferenceException e) {
            LOG.debug("{} changed while checking if it is shown", Locators.nameOf(by));
            return false;
        }
    }

    public void selectByText(By by, String visibleText) {
        new Select(waitVisible(by)).selectByVisibleText(visibleText);
        LOG.info("Choose \"{}\" in {}", visibleText, Locators.nameOf(by));
    }

    public void selectByValue(By by, String value) {
        new Select(waitVisible(by)).selectByValue(value);
        LOG.info("Choose option with value \"{}\" in {}", value, Locators.nameOf(by));
    }

    /** Texts of all VISIBLE matching elements (empty list if none). Never waits, never throws. */
    public List<String> visibleTexts(By by) {
        try {
            return driver.findElements(by).stream()
                    .filter(WebElement::isDisplayed)
                    .map(e -> e.getText().trim())
                    .filter(text -> !text.isEmpty())
                    .toList();
        } catch (StaleElementReferenceException e) {
            return List.of();
        }
    }

    public List<String> optionTexts(By by) {
        return new Select(waitVisible(by)).getOptions().stream().map(WebElement::getText).toList();
    }

    /** LAST RESORT: JavaScript click bypasses real user behaviour. Say why at the call site. */
    public void jsClick(By by) {
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", waitPresent(by));
        LOG.info("Click {} (using JavaScript)", Locators.nameOf(by));
    }

    public void scrollIntoView(By by) {
        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", waitPresent(by));
    }

    // =================================== Waits ==================================

    public WebElement waitVisible(By by) {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(by));
    }

    public WebElement waitClickable(By by) {
        return wait.until(ExpectedConditions.elementToBeClickable(by));
    }

    public WebElement waitPresent(By by) {
        return wait.until(ExpectedConditions.presenceOfElementLocated(by));
    }

    public List<WebElement> waitAllVisible(By by) {
        return wait.until(ExpectedConditions.visibilityOfAllElementsLocatedBy(by));
    }

    public boolean waitInvisible(By by) {
        return wait.until(ExpectedConditions.invisibilityOfElementLocated(by));
    }

    public boolean waitText(By by, String text) {
        return wait.until(ExpectedConditions.textToBePresentInElementLocated(by, text));
    }

    public boolean waitUrlContains(String part) {
        return wait.until(ExpectedConditions.urlContains(part));
    }

    /** ParaBank fills some dropdowns (account lists) after the page loads: wait until options exist. */
    public void waitOptionsLoaded(By select) {
        waitUntil(d -> !new Select(d.findElement(select)).getOptions().isEmpty(), "options loaded in " + select);
    }

    public void waitPageReady() {
        waitUntil(d -> "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState")),
                "document.readyState == complete");
    }

    /**
     * Any custom condition, with a readable timeout message. Uses a fresh wait because withMessage()
     * changes the wait it is called on, and the message would otherwise stick to later timeouts.
     */
    public <T> T waitUntil(Function<WebDriver, T> condition, String description) {
        WebDriverWait w = newWait();
        w.withMessage(description);
        return w.until(condition);
    }

    private WebDriverWait newWait() {
        WebDriverWait w = new WebDriverWait(driver, timeout, polling);
        w.ignoring(StaleElementReferenceException.class);
        return w;
    }
}
