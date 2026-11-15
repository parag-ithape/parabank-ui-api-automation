package pages;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import utils.Actions;
import utils.Config;
import utils.Driver;
import utils.FrameworkException;
import utils.Locators;

/**
 * Parent of all pages and components. It gives every page:
 * <ul>
 *   <li>the current thread's browser (from Driver.get(), never a copied static field)</li>
 *   <li>Actions: interactions + explicit waits</li>
 *   <li>its own section of locators.properties (section = pageName) and the shared "common" locators</li>
 * </ul>
 * Pages contain NO assertions: they return values or state, and steps assert.
 */
public abstract class BasePage {

    protected final WebDriver driver;
    protected final Actions actions;
    protected final Locators loc;
    protected final Locators common = Locators.forPage("common");
    protected final Logger log = LogManager.getLogger(getClass());

    protected BasePage(String pageName) {
        this.driver = Driver.get();
        this.actions = new Actions(driver);
        this.loc = Locators.forPage(pageName);
    }

    /** Each page defines what "loaded" means (an element unique to it). */
    public abstract boolean isLoaded();

    /**
     * Waits until isLoaded() is true. If it never is, fails with what the browser shows INSTEAD
     * (page title, URL, any error message), e.g. "AccountsOverviewPage did not load. The browser shows:
     * title "Error!", URL …/overview.htm, error "An internal error has occurred and has been logged."".
     */
    @SuppressWarnings("unchecked")
    protected <T extends BasePage> T waitUntilLoaded() {
        try {
            actions.waitUntil(d -> isLoaded(), getClass().getSimpleName() + " did not load");
        } catch (TimeoutException e) {
            throw new FrameworkException(getClass().getSimpleName() + " did not load within "
                    + Config.get(Config.TIMEOUT_EXPLICIT) + " s. The browser shows: " + describeCurrentPage(), e);
        }
        return (T) this;
    }

    /** Title, URL and error message of whatever page is open now. Never throws. */
    protected String describeCurrentPage() {
        String title = actions.visibleTexts(common.by("page.title")).stream().findFirst().orElse("(none)");
        String error = isErrorShown() ? getErrorText() : "(none)";
        String url;
        try {
            url = driver.getCurrentUrl();
        } catch (WebDriverException e) {
            url = "(unknown)";
        }
        return "title \"" + title + "\", URL " + url + ", error \"" + error + "\"";
    }

    /** Opens a ParaBank page by its path, e.g. "/index.htm". */
    protected void openPath(String path) {
        String url = Config.get(Config.BASE_URL) + path;
        log.info("Open {}", url);
        driver.get(url);
    }

    /** True if the right-hand panel shows an error (validation message or ParaBank's "Error!" page). Never throws. */
    public boolean isErrorShown() {
        return actions.isDisplayed(common.by("error.panel"));
    }

    /** Text of the error in the right-hand panel; call only when isErrorShown() is true. */
    public String getErrorText() {
        return actions.text(common.by("error.panel"));
    }

    /** Title in the right-hand panel (h1.title). */
    public String getPageTitle() {
        return actions.text(common.by("page.title"));
    }
}
