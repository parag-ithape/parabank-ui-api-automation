package stepdefinitions;

import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import pages.AccountsOverviewPage;
import pages.BankCustomer;
import pages.LeftMenuComponent;
import pages.LoginPage;
import utils.Messages;
import utils.ScenarioContext;
import utils.TestData;

import static org.assertj.core.api.Assertions.assertThat;

/** Login and logout (LOG-01 to LOG-04). Steps call pages, then assert; pages never assert. */
public class LoginSteps {

    @When("they log in with their username and password")
    public void theyLogIn() {
        BankCustomer customer = ScenarioContext.get(ScenarioContext.CUSTOMER);
        new LoginPage().open().loginAs(customer);
    }

    @When("they log in with a wrong password")
    public void theyLogInWithAWrongPassword() {
        BankCustomer customer = ScenarioContext.get(ScenarioContext.CUSTOMER);
        new LoginPage().open().attemptLogin(customer.username(), TestData.password());
    }

    @When("they log in without a username and password")
    public void theyLogInWithoutCredentials() {
        new LoginPage().attemptLogin("", "");
    }

    @When("they log out")
    public void theyLogOut() {
        new LeftMenuComponent().logOut();
    }

    @When("they open the accounts overview page directly")
    public void theyOpenTheAccountsOverviewPageDirectly() {
        new AccountsOverviewPage().openDirectly();
    }

    @Then("their accounts overview is shown")
    public void theirAccountsOverviewIsShown() {
        assertThat(new AccountsOverviewPage().getTitle())
                .as("Title of the page shown after login")
                .isEqualTo(Messages.get("overview.title"));
    }

    @Then("they are told the username and password could not be verified")
    public void theyAreToldCredentialsCouldNotBeVerified() {
        assertThat(new LoginPage().getErrorMessage())
                .as("Login error for a wrong password")
                .isEqualTo(Messages.get("login.error.invalid"));
    }

    @Then("they are asked to enter a username and password")
    public void theyAreAskedToEnterCredentials() {
        assertThat(new LoginPage().getErrorMessage())
                .as("Login error for empty username and password")
                .isEqualTo(Messages.get("login.error.empty"));
    }

    @Then("the login form is shown")
    public void theLoginFormIsShown() {
        assertThat(new LoginPage().isLoaded())
                .as("Login form is shown after logout")
                .isTrue();
    }

    @Then("no account data is visible")
    public void noAccountDataIsVisible() {
        assertThat(new AccountsOverviewPage().isAccountDataVisible())
                .as("Account table or account numbers visible after logout")
                .isFalse();
    }
}
