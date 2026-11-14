# ParaBank UI + API Test Plan

Nov 14, 2026 · Parag Ithape

## Scope and approach

This plan covers automated functional testing of ParaBank's core banking flows through the web UI (Selenium + Cucumber BDD) and the REST API (REST Assured), including hybrid scenarios that set up data through the API and verify UI actions through it. It drives the `parabank-ui-api-automation` repo.

**Layers**

- **UI:** what a customer sees and does in the browser.
- **API:** banking rules checked directly against `/services/bank`, no browser.
- **Hybrid:** API sets up accounts and balances, the UI performs the action, the API confirms the result.

**Environments:** the public demo at https://parabank.parasoft.com/parabank for local runs; a private `parasoft/parabank` Docker container for CI.

**Out of scope:** performance (separate JMeter repo), security/penetration testing, the admin page, the SOAP and JMS interfaces, visual and accessibility testing.

## Features under test

Eleven customer-facing features are in scope, all reachable from the login panel or the "Account Services" menu after login. Confirm page details against the live site while building.

| Feature | Where | What the customer does | API coverage |
| --- | --- | --- | --- |
| Registration | Login panel → Register | Creates a customer with name, address, phone, SSN, username, password | None (no REST endpoint) |
| Login / logout | Login panel; Account Services → Log Out | Logs in with username and password | `GET /login/{u}/{p}` |
| Forgot login info | Login panel → Forgot login info? | Recovers username/password with name, address, SSN | None |
| Accounts overview | Account Services → Accounts Overview | Sees accounts, balances, available amounts, total | `GET /customers/{id}/accounts` |
| Account activity | Click an account number | Sees account details and transaction list, filters by period/type | `GET /accounts/{id}`, `/accounts/{id}/transactions` |
| Open new account | Account Services → Open New Account | Opens checking or savings, funded from an existing account | `POST /createAccount` |
| Transfer funds | Account Services → Transfer Funds | Moves an amount between own accounts | `POST /transfer` |
| Bill pay | Account Services → Bill Pay | Pays a payee (name, address, phone, account) from an account | `POST /billpay` |
| Find transactions | Account Services → Find Transactions | Searches by transaction ID, date, date range or amount | `GET /accounts/{id}/transactions/...` |
| Update contact info | Account Services → Update Contact Info | Edits name, address, phone | `POST /customers/update/{id}` |
| Request loan | Account Services → Request Loan | Requests a loan with amount, down payment, funding account; sees Approved/Denied | `POST /requestLoan` |

Loan decisions depend on the bank's configured loan processor (available funds, down payment, or combined, with a threshold), so loan tests must create a customer state that clearly meets or clearly fails the rule.

## Test cases

46 cases across 11 features: 28 UI, 11 API (including security and contract) and 7 hybrid. The 16 P1 cases form the `@smoke` suite; everything runs in `@regression`. Where ParaBank may not enforce a banking rule (marked ⚠️), record the real behaviour on the first run; if it differs, keep the expectation and tag the scenario `@knownIssue`.

| ID | Feature | Scenario | Layer | Type | Priority | Expected result |
| --- | --- | --- | --- | --- | --- | --- |
| REG-01 | Registration | Register with valid details | UI | Positive | P1 | Welcome message with username; customer is logged in |
| REG-02 | Registration | Register with a username that already exists | UI | Negative | P1 | Duplicate-username error; no account created |
| REG-03 | Registration | Password and confirmation don't match | UI | Negative | P2 | Password-mismatch error |
| REG-04 | Registration | Submit with required fields empty | UI | Negative | P2 | Required-field error under each empty field |
| LOG-01 | Login | Log in with valid credentials | UI | Positive | P1 | Accounts overview shown |
| LOG-02 | Login | Log in with wrong password | UI | Negative | P1 | Credentials-not-verified error |
| LOG-03 | Login | Log in with empty username and password | UI | Negative | P2 | Enter-username-and-password error |
| LOG-04 | Logout | Log out, then open an account page directly | UI | Security | P2 | Login page shown; account data not visible |
| LOG-05 | Login | API login with valid credentials | API | Positive | P1 | 200; customer ID, first and last name match registration |
| LOG-06 | Login | API login with invalid credentials | API | Negative | P2 | Error response; no customer data |
| FGT-01 | Forgot login | Recover login with correct name, address and SSN | UI | Positive | P3 | Username and password shown |
| FGT-02 | Forgot login | Recover with wrong SSN | UI | Negative | P3 | Customer-not-found error |
| ACC-01 | Accounts overview | New customer sees their first account | UI | Positive | P1 | One account listed with the initial balance |
| ACC-02 | Accounts overview | Total equals the sum of account balances | UI | Positive | P2 | Total = sum of balance column |
| ACC-03 | Accounts overview | UI overview matches API account list | Hybrid | Positive | P2 | Same account IDs and balances in UI and API |
| ACC-04 | Account activity | Activity shows the opening deposit | UI | Positive | P3 | Opening transaction listed with correct amount |
| OPN-01 | Open account | Open a savings account funded from checking | UI | Positive | P1 | Account-opened confirmation with new account ID |
| OPN-02 | Open account | Open a checking account | UI | Positive | P2 | Confirmation; new account type is checking |
| OPN-03 | Open account | API create savings account | API | Positive | P1 | 200; type SAVINGS, correct customer ID |
| OPN-04 | Open account | Opening deposit moves from the funding account | Hybrid | Positive | P1 | Funding balance drops by the opening deposit; new account holds it |
| TRF-01 | Transfer | Transfer between own accounts in the UI | UI | Positive | P1 | Transfer-complete page shows amount and both accounts |
| TRF-02 | Transfer | UI transfer updates both balances | Hybrid | Positive | P1 | API: source −amount, target +amount |
| TRF-03 | Transfer | API transfer between own accounts | API | Positive | P1 | 200; both balances updated |
| TRF-04 | Transfer | ⚠️ Transfer more than the available balance | API | Negative | P1 | Rejected; source balance unchanged |
| TRF-05 | Transfer | Transfer with empty or non-numeric amount | UI | Negative | P2 | Amount validation error; nothing transferred |
| TRF-06 | Transfer | ⚠️ Transfer zero or a negative amount | API | Negative | P2 | Rejected; balances unchanged |
| TRF-07 | Transfer | Transfer appears in both accounts' activity | Hybrid | Positive | P2 | Debit in source, credit in target, same amount and date |
| BIL-01 | Bill pay | Pay a bill from checking | UI | Positive | P1 | Bill-payment-complete page; payee and amount shown |
| BIL-02 | Bill pay | Bill payment reduces the balance | Hybrid | Positive | P2 | API: balance −amount; transaction names the payee |
| BIL-03 | Bill pay | Submit without payee name | UI | Negative | P2 | Payee-name-required error |
| BIL-04 | Bill pay | Account number and verify-account don't match | UI | Negative | P2 | Account-mismatch error |
| BIL-05 | Bill pay | Non-numeric amount | UI | Negative | P2 | Amount validation error |
| BIL-06 | Bill pay | API bill payment | API | Positive | P2 | 200; balance reduced by the amount |
| FND-01 | Find transactions | Find by amount | UI | Positive | P2 | Matching transaction listed |
| FND-02 | Find transactions | Find by today's date | UI | Positive | P2 | Today's transactions listed |
| FND-03 | Find transactions | Find by transaction ID | UI | Positive | P3 | Exactly that transaction listed |
| FND-04 | Find transactions | Invalid date format | UI | Negative | P3 | Date validation error |
| FND-05 | Find transactions | API find by amount | API | Positive | P2 | 200; every result has that amount |
| UPD-01 | Update contact info | Update phone and address | Hybrid | Positive | P2 | Profile-updated message; API returns the new values |
| UPD-02 | Update contact info | Clear a required field | UI | Negative | P3 | Required-field error; old values kept |
| LON-01 | Request loan | Loan with affordable down payment | UI | Positive | P1 | Status Approved; new loan account number shown |
| LON-02 | Request loan | Down payment above available funds | UI | Negative | P1 | Status Denied with reason |
| LON-03 | Request loan | Approved loan creates a loan account | Hybrid | Positive | P2 | API: new LOAN account; down payment deducted from funding account |
| LON-04 | Request loan | API loan request | API | Positive | P2 | 200; approval flag and loan account ID returned |
| SEC-01 | Security | ⚠️ API returns another customer's accounts without auth | API | Security | P3 | Access refused (likely a finding on a demo app) |
| CON-01 | Contract | Schema check on customer, account and transaction responses | API | Contract | P2 | Responses match JSON schemas |

## Test data and setup

Every scenario owns its data, so scenarios run in any order and in parallel without colliding on the shared public site.

- **Customers:** generated with unique usernames (`bdd_<timestamp>_<thread>`) and generated passwords. One customer is registered through the UI per thread and reused; ParaBank has no registration API.
- **Accounts and balances:** created through the API at the start of each scenario (open account, transfer to set balances), then stored in the scenario context.
- **Payees and addresses:** from `payees.json` and `customers.json`; no real personal data.
- **Loan cases:** LON-01 uses a down payment well inside available funds; LON-02 uses a down payment larger than the funding account balance, so the outcome doesn't depend on edge thresholds.
- **Money:** all amounts compared as exact decimals (e.g. 300.00), never floating point.
- **CI:** a fresh ParaBank container per run, so CI data never mixes with other users of the public demo.

## Risks and open questions

- **Public site is shared and resets:** data can disappear mid-run, and other users load the site. Keep local parallelism at 2–3 and use the CI container for full runs.
- **Page details unverified:** field names, locators, messages and API paths in this plan come from ParaBank's known structure, not a live check. Confirm them on the site and at `<baseUrl>/services/bank?_wadl` before automating.
- **⚠️ Business rules ParaBank may not enforce** (TRF-04, TRF-06, SEC-01): keep the correct expectation, tag `@knownIssue` if the app disagrees, and list the finding in the README.
- **Loan outcome depends on bank settings** (loan processor type and threshold on the admin page). The CI container uses defaults; don't change admin settings on the public site.
- **Initial and minimum balances** are configurable on the admin page; read actual balances through the API instead of hard-coding them.
