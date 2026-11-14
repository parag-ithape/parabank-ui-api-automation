package runner;

import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import org.apache.logging.log4j.ThreadContext;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import utils.AllureAttachments;
import utils.Config;
import utils.Driver;
import utils.ScenarioContext;

/** Starts and stops the browser around @ui scenarios, and cleans up after every scenario. */
public class Hooks {

    /** Log lines carry the scenario name, so parallel output stays readable. */
    @Before(order = 0)
    public void nameLogLines(Scenario scenario) {
        ThreadContext.put("scenario", scenario.getName().replaceAll("\\W+", "_"));
    }

    /** API-only scenarios never open a browser, so they stay fast. */
    @Before(value = "@ui", order = 1)
    public void startBrowser() {
        ThreadContext.put("browser", Config.get(Config.BROWSER));
        Driver.start();
    }

    /** Evidence is attached BEFORE quitting: once the browser is gone, no screenshot can be taken. */
    @After(value = "@ui", order = 1)
    public void stopBrowser(Scenario scenario) {
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
            Driver.quit();      // always, even if attaching failed
        }
    }

    /** Runs last for every scenario, UI or API: nothing leaks into the next scenario on this thread. */
    @After(order = 0)
    public void clearContext() {
        ScenarioContext.clear();
        ThreadContext.clearAll();
    }
}
