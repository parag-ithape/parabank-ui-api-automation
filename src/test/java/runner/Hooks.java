package runner;

import io.cucumber.java.After;
import io.cucumber.java.AfterAll;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import org.apache.logging.log4j.ThreadContext;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import utils.AllureAttachments;
import utils.Config;
import utils.Driver;
import utils.ScenarioContext;

/**
 * Browser handling around @ui scenarios, and clean-up after every scenario.
 * With browser.reuse=true (default) ONE browser serves the whole run: its session is cleared after each
 * scenario, so the next one starts logged out, and it is closed when all scenarios are done.
 */
public class Hooks {

    /** The log file tags each line with the scenario, so lines stay traceable. */
    @Before(order = 0)
    public void nameLogLines(Scenario scenario) {
        ThreadContext.put("scenario", scenario.getName().replaceAll("\\W+", "_"));
    }

    /** API-only scenarios never open a browser, so they stay fast. */
    @Before(value = "@ui", order = 1)
    public void openBrowser() {
        ThreadContext.put("browser", Config.get(Config.BROWSER));
        Driver.open();
    }

    /** Evidence is attached BEFORE the session is cleared: afterwards the failing page is gone. */
    @After(value = "@ui", order = 1)                 // @After hooks run highest order first
    public void releaseBrowser(Scenario scenario) {
        try {
            if (scenario.isFailed() && Driver.isStarted()) {
                if (Config.getBoolean(Config.SCREENSHOT_ON_FAILURE)) {
                    byte[] png = ((TakesScreenshot) Driver.get()).getScreenshotAs(OutputType.BYTES);
                    scenario.attach(png, "image/png", "Failure screenshot");
                }
                if (Config.getBoolean(Config.ATTACH_PAGE_SOURCE_ON_FAILURE)) {
                    AllureAttachments.pageSource();
                }
                AllureAttachments.browserConsoleLogs();
            }
        } finally {
            Driver.release();   // clears the session (or closes the browser), even if attaching failed
        }
    }

    /** Runs last for every scenario, UI or API: nothing leaks into the next scenario on this thread. */
    @After(order = 0)
    public void clearContext() {
        ScenarioContext.clear();
        ThreadContext.clearAll();
    }

    /** After the last scenario: close the browser(s). */
    @AfterAll
    public static void closeBrowsers() {
        Driver.quitAll();
    }
}
