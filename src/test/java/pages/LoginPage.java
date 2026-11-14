package pages;

import io.qameta.allure.Step;
import org.openqa.selenium.By;

/** The login panel shown on every page while logged out. */
public class LoginPage extends BasePage {

    // Resolved at construction, so a missing locator key fails immediately with the page and key name
    private final By username = loc.by("username.input");
    private final By password = loc.by("password.input");
    private final By loginButton = loc.by("login.button");
    private final By registerLink = loc.by("register.link");
    private final By error = loc.by("error.message");

    public LoginPage() {
        super("login");
    }

    @Step("Open ParaBank")
    public LoginPage open() {
        openPath("/index.htm");
        return waitUntilLoaded();
    }

    /** Happy path: returns the accounts overview. The password never appears in the step title or logs. */
    @Step("Log in as '{customer.username}'")
    public AccountsOverviewPage loginAs(BankCustomer customer) {
        submit(customer.username(), customer.password());
        return new AccountsOverviewPage().waitUntilLoaded();
    }

    /** Negative path: stays on the login page so the error can be read. */
    @Step("Attempt login as '{user}'")
    public LoginPage attemptLogin(String user, String pass) {
        submit(user, pass);
        return this;
    }

    @Step("Go to registration")
    public RegisterPage goToRegistration() {
        actions.click(registerLink);
        return new RegisterPage().waitUntilLoaded();
    }

    public String getErrorMessage() {
        return actions.text(error);
    }

    @Override
    public boolean isLoaded() {
        return actions.isDisplayed(loginButton);
    }

    private void submit(String user, String pass) {
        actions.type(username, user);
        actions.typeSecret(password, pass);
        actions.click(loginButton);
    }
}
