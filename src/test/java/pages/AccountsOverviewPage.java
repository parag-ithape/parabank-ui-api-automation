package pages;

import io.qameta.allure.Step;
import org.openqa.selenium.By;

/** Accounts Overview (overview.htm): the first page after login. */
public class AccountsOverviewPage extends BasePage {

    private final By title = loc.by("page.title");
    private final By accountTable = loc.by("account.table");
    private final By accountLinks = loc.by("account.links");

    public AccountsOverviewPage() {
        super("accounts-overview");
    }

    /**
     * Opens overview.htm by URL, without clicking through the menu. Does NOT wait for the page to load,
     * because it is also used to check what a logged-out visitor sees.
     */
    @Step("Open the accounts overview page directly by URL")
    public AccountsOverviewPage openDirectly() {
        openPath("/overview.htm");
        actions.waitPageReady();
        return this;
    }

    public String getTitle() {
        return actions.text(title);
    }

    /** True if any account data (the table or an account number) is visible. Never throws. */
    public boolean isAccountDataVisible() {
        return actions.isDisplayed(accountTable) || actions.isDisplayed(accountLinks);
    }

    @Override
    public boolean isLoaded() {
        return actions.isDisplayed(accountTable);
    }
}
