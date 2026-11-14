package stepdefinitions;

import io.cucumber.java.en.Given;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pages.BankCustomer;
import pages.LeftMenuComponent;
import pages.LoginPage;
import pages.RegisterPage;
import utils.FrameworkException;
import utils.ScenarioContext;

/**
 * Shared "Given" steps that provide a customer.
 *
 * <p>ParaBank has no registration API, so customers are registered through the UI. To keep runs fast,
 * each thread registers ONE customer and reuses it in later scenarios on the same thread. Scenarios
 * that need a brand-new customer (registration tests) generate their own.</p>
 */
public class CustomerSteps {

    private static final Logger LOG = LogManager.getLogger(CustomerSteps.class);

    /** Per thread, never shared between parallel threads. */
    private static final ThreadLocal<BankCustomer> REGISTERED = new ThreadLocal<>();

    @Given("a registered customer")
    public void aRegisteredCustomer() {
        BankCustomer customer = REGISTERED.get();
        if (customer == null) {
            customer = BankCustomer.generate();
            LOG.info("Registering a new customer \"{}\" for this test run", customer.username());
            long start = System.nanoTime();
            RegisterPage page = new RegisterPage().open().register(customer);
            LOG.info("Registration answered in {} s", String.format("%.1f", (System.nanoTime() - start) / 1e9));
            LeftMenuComponent menu = new LeftMenuComponent();
            if (!menu.isLoaded()) {                 // registration logs the customer in; no menu = refused
                throw new FrameworkException("Could not register the test customer \"" + customer.username()
                        + "\". Page title: \"" + page.getPageTitle() + "\". Shown on the page: \""
                        + page.describeErrorsShown() + "\"");
            }
            start = System.nanoTime();
            menu.logOut();                          // start the scenario logged out
            LOG.info("Logged out again in {} s", String.format("%.1f", (System.nanoTime() - start) / 1e9));
            REGISTERED.set(customer);
        } else {
            LOG.info("Using customer \"{}\", registered earlier in this run", customer.username());
        }
        ScenarioContext.put(ScenarioContext.CUSTOMER, customer);
    }

    @Given("a registered customer who is logged in")
    public void aRegisteredCustomerWhoIsLoggedIn() {
        aRegisteredCustomer();
        new LoginPage().open().loginAs(ScenarioContext.get(ScenarioContext.CUSTOMER));
    }

    @Given("a visitor on the login page")
    public void aVisitorOnTheLoginPage() {
        new LoginPage().open();
    }

    @Given("a visitor on the registration page")
    public void aVisitorOnTheRegistrationPage() {
        new LoginPage().open().goToRegistration();
    }
}
