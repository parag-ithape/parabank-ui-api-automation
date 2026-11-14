@ui @regression
Feature: Customer login and logout
  A registered customer logs in to see their accounts, and logs out to protect them.

  @smoke @LOG-01
  Scenario: Registered customer logs in
    Given a registered customer
    When they log in with their username and password
    Then their accounts overview is shown

  @smoke @LOG-02
  Scenario: Login is refused with a wrong password
    Given a registered customer
    When they log in with a wrong password
    Then they are told the username and password could not be verified

  @LOG-03
  Scenario: Login is refused when username and password are empty
    Given a visitor on the login page
    When they log in without a username and password
    Then they are asked to enter a username and password

  # Known ParaBank defect: overview.htm after logout answers HTTP 500 "An internal error has occurred"
  # instead of a normal login page. Excluded from the default run; see README "Findings".
  @LOG-04 @knownIssue
  Scenario: After logging out, account pages are no longer available
    Given a registered customer who is logged in
    When they log out
    And they open the accounts overview page directly
    Then the login form is shown
    And no error page is shown
    And no account data is visible
