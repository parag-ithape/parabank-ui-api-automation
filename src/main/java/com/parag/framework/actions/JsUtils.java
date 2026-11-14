package com.parag.framework.actions;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

/** Use JavaScript ONLY as a last resort (it bypasses real user behaviour). Document why at each call site. */
public class JsUtils {

    private final JavascriptExecutor js;

    public JsUtils(WebDriver driver) {
        this.js = (JavascriptExecutor) driver;
    }

    public void click(WebElement el) {
        js.executeScript("arguments[0].click();", el);
    }

    public void scrollIntoView(WebElement el) {
        js.executeScript("arguments[0].scrollIntoView({block:'center'});", el);
    }
}
