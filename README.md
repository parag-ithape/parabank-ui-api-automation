# ParaBank UI + API Test Automation

Test automation for the [ParaBank](https://parabank.parasoft.com/parabank) demo banking app, covering the **UI with Selenium + Cucumber BDD** and the **API with REST Assured** in a single Java framework.

> 🚧 **In progress.** Scenarios are being added feature by feature. See the coverage table below.

## Why this project

Banks use BDD to describe business rules in plain language. This suite tests ParaBank's core banking flows (accounts, transfers, bill pay and loans) the way a bank QA team would:

- **UI tests** check what the customer sees and does
- **API tests** check the business rules directly
- **Hybrid scenarios** use the API for fast test setup and to verify UI actions (e.g. transfer in the browser, confirm balances through the API)

## Tech stack

Java 17 · Selenium WebDriver · Cucumber BDD · TestNG · REST Assured · Maven · GitHub Actions

## Test coverage

| Feature | Scenarios | Layer | Status |
| --- | --- | --- | --- |
| Customer registration | Valid registration, duplicate username, password mismatch, required fields (REG-01–04) | UI | ✅ |
| Login / logout | Valid, wrong password, empty fields, no access after logout (LOG-01–04) | UI | ✅ |
| Accounts overview | Opening balance shown correctly | UI + API | 🔜 |
| Open account | New savings account is created | UI + API | 🔜 |
| Transfer funds | Transfer between own accounts updates both balances | Hybrid | 🔜 |
| Transfer rules | Transfer above balance is rejected | UI + API | 🔜 |
| Bill pay | Valid payment, missing-field validation | UI | 🔜 |
| Find transactions | Search by amount | UI + API | 🔜 |
| Request loan | Approved vs denied based on down payment | UI + API | 🔜 |

## Project structure

```
src/test/java/
├── pages/              One class per ParaBank screen
├── stepdefinitions/    Code behind each Gherkin step
├── runner/             Cucumber runner + hooks (browser start/stop, screenshots)
└── utils/              Config, browser, locators, waits, test data
src/test/resources/
├── features/           Test scenarios in plain English (.feature), tagged @ui / @api
├── locators/           One locator file per page
├── config.properties   Browser, timeouts…  (+ env-public / env-local for the URL)
└── testng.xml          What runs
```

Full design: [docs/FRAMEWORK_DESIGN.md](docs/FRAMEWORK_DESIGN.md) · Test cases: [docs/TEST_PLAN.md](docs/TEST_PLAN.md)

## How to run

```bash
# all tests (public ParaBank site, Chrome)
mvn clean test

# smoke only, headless
mvn clean test -Dcucumber.filter.tags="@smoke" -Dheadless=true

# one test case from the test plan
mvn clean test -Dcucumber.filter.tags="@LOG-01"

# only UI or only API
mvn test -Dcucumber.filter.tags="@ui"
mvn test -Dcucumber.filter.tags="@api"
```

Scenarios run one after another in a single visible Chrome window, and the console shows each scenario, step and action in plain language, with a summary at the end. Add `-Dheadless=true` to hide the browser, or `-DdpThreads=2` to run 2 scenarios at a time.

The target environment is the public ParaBank site by default (`env-public.properties`). For a local Docker instance run `docker run -d -p 8080:8080 parasoft/parabank`, then add `-Plocal` to the mvn command.

## Reports

After a run, the console summary shows where the reports are:

- **Cucumber report:** double-click `target/cucumber-report.html`. Every feature, scenario and step, with screenshots of failures.
- **Allure report:** run `mvn allure:report`, then double-click `target/allure-report/index.html`. Charts, failure categories, timeline and full details per step.

## Related projects

- [Performance tests on ParaBank (JMeter)](https://github.com/parag-ithape/performance-test-jmeter)
- [E-commerce API test automation (REST Assured)](https://github.com/parag-ithape/ecom-api-test)

## Author

**Parag Ithape**, Senior QA Engineer / SDET · [Portfolio](https://parag-ithape.github.io) · [LinkedIn](https://www.linkedin.com/in/parag-ithape)
