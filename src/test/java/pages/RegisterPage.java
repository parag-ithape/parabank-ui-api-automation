package pages;

import io.qameta.allure.Step;
import org.openqa.selenium.By;

import java.util.Locale;

/** Registration form (register.htm). */
public class RegisterPage extends BasePage {

    private final By firstName = loc.by("firstName.input");
    private final By lastName = loc.by("lastName.input");
    private final By street = loc.by("street.input");
    private final By city = loc.by("city.input");
    private final By state = loc.by("state.input");
    private final By zipCode = loc.by("zipCode.input");
    private final By phone = loc.by("phone.input");
    private final By ssn = loc.by("ssn.input");
    private final By username = loc.by("username.input");
    private final By password = loc.by("password.input");
    private final By confirmPassword = loc.by("confirmPassword.input");
    private final By registerButton = loc.by("register.button");
    private final By welcomeText = loc.by("welcome.text");

    public RegisterPage() {
        super("register");
    }

    @Step("Open the registration page")
    public RegisterPage open() {
        openPath("/register.htm");
        return waitUntilLoaded();
    }

    @Step("Register '{customer.username}'")
    public RegisterPage register(BankCustomer customer) {
        return register(customer, customer.password());
    }

    /** Same as register(), but with a separate value in "Confirm" (to test the mismatch rule). */
    @Step("Register '{customer.username}' with a different password confirmation")
    public RegisterPage register(BankCustomer customer, String confirmation) {
        actions.type(firstName, customer.firstName());
        actions.type(lastName, customer.lastName());
        actions.type(street, customer.street());
        actions.type(city, customer.city());
        actions.type(state, customer.state());
        actions.type(zipCode, customer.zipCode());
        actions.type(phone, customer.phone());
        actions.type(ssn, customer.ssn());
        actions.type(username, customer.username());
        actions.typeSecret(password, customer.password());
        actions.typeSecret(confirmPassword, confirmation);
        actions.click(registerButton);
        return this;
    }

    @Step("Submit the registration form empty")
    public RegisterPage submitEmpty() {
        actions.click(registerButton);
        return this;
    }

    /** "Your account was created successfully…" paragraph under the welcome title. */
    public String getWelcomeText() {
        return actions.text(welcomeText);
    }

    /**
     * Error under one field, by its business name as written in a feature file:
     * "first name" → firstName.error, "zip code" → zipCode.error, "confirm password" → confirmPassword.error.
     */
    public String getFieldError(String fieldName) {
        return actions.text(loc.by(fieldKey(fieldName) + ".error"));
    }

    /** True while the form is still shown, e.g. after the registration was refused. */
    public boolean isFormDisplayed() {
        return actions.isDisplayed(registerButton);
    }

    @Override
    public boolean isLoaded() {
        return isFormDisplayed();
    }

    /** "zip code" → "zipCode". Also used by steps to build the matching message key. */
    public static String fieldKey(String fieldName) {
        String[] words = fieldName.trim().toLowerCase(Locale.ROOT).split("\\s+");
        StringBuilder key = new StringBuilder(words[0]);
        for (int i = 1; i < words.length; i++) {
            key.append(Character.toUpperCase(words[i].charAt(0))).append(words[i].substring(1));
        }
        return key.toString();
    }
}
