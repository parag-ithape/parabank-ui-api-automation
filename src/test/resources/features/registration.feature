@ui @regression
Feature: Customer registration
  A visitor opens an online banking profile from the "Register" link on the login panel.

  @smoke @REG-01
  Scenario: Visitor registers with valid details
    Given a visitor on the registration page
    When they register with valid details
    Then they see a welcome message with their username
    And they are logged in

  @smoke @REG-02
  Scenario: Registration is refused for a username that already exists
    Given a registered customer
    And a visitor on the registration page
    When they register with that customer's username
    Then they are told the username already exists
    And no account is created

  @REG-03
  Scenario: Registration is refused when the password confirmation does not match
    Given a visitor on the registration page
    When they register with a password confirmation that does not match
    Then they are told the passwords do not match
    And no account is created

  @REG-04
  Scenario: Every empty required field is reported
    Given a visitor on the registration page
    When they submit the registration form empty
    Then these fields are reported as required:
      | first name       |
      | last name        |
      | street           |
      | city             |
      | state            |
      | zip code         |
      | ssn              |
      | username         |
      | password         |
      | confirm password |
