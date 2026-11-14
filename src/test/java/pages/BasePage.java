package pages;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.WebDriver;
import utils.Actions;
import utils.Config;
import utils.Driver;
import utils.Locators;

/**
 * Parent of all pages and components. It gives every page:
 * <ul>
 *   <li>the current thread's browser (from Driver.get(), never a copied static field)</li>
 *   <li>Actions: interactions + explicit waits</li>
 *   <li>its own locator file (file name = pageName) and the shared "common" locators</li>
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

    /** Waits until isLoaded() is true, failing with the page name if it never is. */
    @SuppressWarnings("unchecked")
    protected <T extends BasePage> T waitUntilLoaded() {
        actions.waitUntil(d -> isLoaded(), getClass().getSimpleName() + " did not load");
        return (T) this;
    }

    /** Opens a ParaBank page by its path, e.g. "/index.htm". */
    protected void openPath(String path) {
        String url = Config.get(Config.BASE_URL) + path;
        log.info("Opening {}", url);
        driver.get(url);
    }

    /** Title in the right-hand panel (h1.title). */
    public String getPageTitle() {
        return actions.text(common.by("page.title"));
    }
}
