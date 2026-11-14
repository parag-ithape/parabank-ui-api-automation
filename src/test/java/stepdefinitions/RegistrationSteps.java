package stepdefinitions;

import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.assertj.core.api.SoftAssertions;
import pages.BankCustomer;
import pages.LeftMenuComponent;
import pages.RegisterPage;
import utils.Messages;
import utils.ScenarioContext;
import utils.TestData;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Registration (REG-01 to REG-04). */
public class RegistrationSteps {

    @When("they register with valid details")
    public void theyRegisterWithValidDetails() {
        BankCustomer newCustomer = BankCustomer.generate();
        ScenarioContext.put(ScenarioContext.CUSTOMER, newCustomer);
        new RegisterPage().register(newCustomer);
    }

    @When("they register with that customer's username")
    public void theyRegisterWithThatCustomersUsername() {
        BankCustomer existing = ScenarioContext.get(ScenarioContext.CUSTOMER);
        new RegisterPage().register(BankCustomer.generate().withUsername(existing.username()));
    }

    @When("they register with a password confirmation that does not match")
    public void theyRegisterWithAMismatchedConfirmation() {
        BankCustomer newCustomer = BankCustomer.generate();
        new RegisterPage().register(newCustomer, TestData.password());
    }

    @When("they submit the registration form empty")
    public void theySubmitTheRegistrationFormEmpty() {
        new RegisterPage().submitEmpty();
    }

    @Then("they see a welcome message with their username")
    public void theySeeAWelcomeMessage() {
        BankCustomer customer = ScenarioContext.get(ScenarioContext.CUSTOMER);
        RegisterPage page = new RegisterPage();
        assertThat(page.getPageTitle())
                .as("Welcome title after registration")
                .isEqualTo(Messages.get("register.success.title", customer.username()));
        assertThat(page.getWelcomeText())
                .as("Confirmation text after registration")
                .isEqualTo(Messages.get("register.success.text"));
    }

    @Then("they are logged in")
    public void theyAreLoggedIn() {
        assertThat(new LeftMenuComponent().isLoaded())
                .as("Account Services menu (with Log Out) is shown")
                .isTrue();
    }

    @Then("they are told the username already exists")
    public void theyAreToldTheUsernameAlreadyExists() {
        assertThat(new RegisterPage().getFieldError("username"))
                .as("Error under the username field")
                .isEqualTo(Messages.get("register.error.username.exists"));
    }

    @Then("they are told the passwords do not match")
    public void theyAreToldThePasswordsDoNotMatch() {
        assertThat(new RegisterPage().getFieldError("confirm password"))
                .as("Error under the password confirmation field")
                .isEqualTo(Messages.get("register.error.password.mismatch"));
    }

    @Then("no account is created")
    public void noAccountIsCreated() {
        assertThat(new RegisterPage().isFormDisplayed())
                .as("Registration form is still shown")
                .isTrue();
        assertThat(new LeftMenuComponent().isLoaded())
                .as("Account Services menu (customer logged in)")
                .isFalse();
    }

    /** Checks every listed field and reports all wrong ones together. */
    @Then("these fields are reported as required:")
    public void theseFieldsAreReportedAsRequired(List<String> fields) {
        RegisterPage page = new RegisterPage();
        SoftAssertions softly = new SoftAssertions();
        for (String field : fields) {
            softly.assertThat(page.getFieldError(field))
                    .as("Required-field error under '%s'", field)
                    .isEqualTo(Messages.get("register.error." + RegisterPage.fieldKey(field) + ".required"));
        }
        softly.assertAll();
    }
}
