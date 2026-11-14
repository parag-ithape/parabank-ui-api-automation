package com.parag.framework.actions;

import com.parag.framework.config.ConfigKeys;
import com.parag.framework.config.ConfigManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.By;
import org.openqa.selenium.ElementClickInterceptedException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.Select;

import java.util.List;

/**
 * Every interaction with the browser goes through here, so waits, retries and logging live in ONE place.
 * Pages call these methods and never call driver.findElement(...).click() directly.
 */
public class ElementActions {

    private static final Logger LOG = LogManager.getLogger(ElementActions.class);

    private final WebDriver driver;
    private final WaitUtils wait;
    private final int clickRetries = ConfigManager.getInt(ConfigKeys.CLICK_RETRY_COUNT);

    public ElementActions(WebDriver driver, WaitUtils wait) {
        this.driver = driver;
        this.wait = wait;
    }

    /** Click with a retry on stale/intercepted elements (page re-renders, overlays). */
    public void click(By by) {
        for (int attempt = 1; ; attempt++) {
            try {
                wait.clickable(by).click();
                LOG.info("Clicked {}", by);
                return;
            } catch (StaleElementReferenceException | ElementClickInterceptedException e) {
                if (attempt >= clickRetries) {
                    throw e;
                }
                LOG.warn("Retry {}/{} click on {} due to {}", attempt, clickRetries, by, e.getClass().getSimpleName());
            }
        }
    }

    /** Clears, then types. Use typeSecret() for passwords so the value is never logged. */
    public void type(By by, String text) {
        WebElement el = wait.visible(by);
        el.clear();
        el.sendKeys(text);
        LOG.info("Typed '{}' into {}", text, by);
    }

    public void typeSecret(By by, String secret) {
        WebElement el = wait.visible(by);
        el.clear();
        el.sendKeys(secret);
        LOG.info("Typed ****** into {}", by);
    }

    public String text(By by) {
        return wait.visible(by).getText().trim();
    }

    /** HTML attribute as written in the markup (WebElement.getAttribute is deprecated in Selenium 4.27+). */
    public String attribute(By by, String name) {
        return wait.present(by).getDomAttribute(name);
    }

    /** Current value of an input or select, including what the user typed (DOM property, not attribute). */
    public String value(By by) {
        return wait.present(by).getDomProperty("value");
    }

    /** Non-throwing visibility check: returns false instead of throwing when the element is absent. */
    public boolean isDisplayed(By by) {
        try {
            return driver.findElements(by).stream().anyMatch(WebElement::isDisplayed);
        } catch (StaleElementReferenceException e) {
            LOG.debug("Element {} went stale during visibility check", by);
            return false;
        }
    }

    public void selectByText(By by, String visibleText) {
        new Select(wait.visible(by)).selectByVisibleText(visibleText);
        LOG.info("Selected '{}' in {}", visibleText, by);
    }

    public void selectByValue(By by, String value) {
        new Select(wait.visible(by)).selectByValue(value);
        LOG.info("Selected value '{}' in {}", value, by);
    }

    public List<String> optionTexts(By by) {
        return new Select(wait.visible(by)).getOptions().stream().map(WebElement::getText).toList();
    }
}
