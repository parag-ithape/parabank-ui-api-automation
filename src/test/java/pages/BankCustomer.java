package pages;

import utils.TestData;

/**
 * A ParaBank customer. Address fields come from testdata/customers.json; names, username and password
 * are generated, so every customer is unique and safe to use in parallel.
 * toString() leaves out the password, so it never appears in logs or the Allure report.
 */
public record BankCustomer(String firstName, String lastName, String street, String city, String state,
                           String zipCode, String phone, String ssn, String username, String password) {

    private static final BankCustomer TEMPLATE =
            TestData.readList("testdata/customers.json", BankCustomer.class).get(0);

    /** A brand-new customer with unique username and generated password. */
    public static BankCustomer generate() {
        return new BankCustomer(TestData.firstName(), TestData.lastName(), TEMPLATE.street(), TEMPLATE.city(),
                TEMPLATE.state(), TEMPLATE.zipCode(), TEMPLATE.phone(), TEMPLATE.ssn(),
                TestData.username(), TestData.password());
    }

    public BankCustomer withUsername(String newUsername) {
        return new BankCustomer(firstName, lastName, street, city, state, zipCode, phone, ssn, newUsername, password);
    }

    @Override
    public String toString() {
        return "BankCustomer[username=" + username + ", name=" + firstName + " " + lastName + "]";
    }
}
