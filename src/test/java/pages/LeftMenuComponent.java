package pages;

import io.qameta.allure.Step;
import org.openqa.selenium.By;

/** The "Account Services" menu shown on every page while logged in. Written once, used by every page. */
public class LeftMenuComponent extends BasePage {

    private final By accountsOverview = loc.by("accounts.overview.link");
    private final By logOut = loc.by("log.out.link");

    public LeftMenuComponent() {
        super("left-menu");
    }

    @Step("Open Accounts Overview from the menu")
    public AccountsOverviewPage openAccountsOverview() {
        actions.click(accountsOverview);
        return new AccountsOverviewPage().waitUntilLoaded();
    }

    @Step("Log out")
    public LoginPage logOut() {
        actions.click(logOut);
        return new LoginPage().waitUntilLoaded();
    }

    /** The menu (with "Log Out") is only shown to a logged-in customer. */
    @Override
    public boolean isLoaded() {
        return actions.isDisplayed(logOut);
    }
}
