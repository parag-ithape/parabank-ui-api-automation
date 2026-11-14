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
| Customer registration | New customer can register | UI | 🔜 |
| Login | Valid, invalid, empty fields | UI | 🔜 |
| Accounts overview | Opening balance shown correctly | UI + API | 🔜 |
| Open account | New savings account is created | UI + API | 🔜 |
| Transfer funds | Transfer between own accounts updates both balances | Hybrid | 🔜 |
| Transfer rules | Transfer above balance is rejected | UI + API | 🔜 |
| Bill pay | Valid payment, missing-field validation | UI | 🔜 |
| Find transactions | Search by amount | UI + API | 🔜 |
| Request loan | Approved vs denied based on down payment | UI + API | 🔜 |

## Project structure

```
src/test/resources/features/
├── ui/       UI scenarios (@ui)
└── api/      API scenarios (@api)
src/test/java/
├── pages/    Selenium page objects (Page Object Model)
├── api/      REST Assured clients for ParaBank's API
├── steps/    Step definitions
├── hooks/    Driver setup, screenshot on failure
└── utils/    Config and test-data builders
```

## How to run

```bash
# all tests
mvn clean test

# only UI or only API
mvn test -Dcucumber.filter.tags="@ui"
mvn test -Dcucumber.filter.tags="@api"
```

The target environment is set by `baseUrl` in `config.properties`: the public ParaBank site, or a local Docker instance (`docker run -d -p 8080:8080 parasoft/parabank`).

## Related projects

- [Performance tests on ParaBank (JMeter)](https://github.com/parag-ithape/performance-test-jmeter)
- [E-commerce API test automation (REST Assured)](https://github.com/parag-ithape/ecom-api-test)

## Author

**Parag Ithape**, Senior QA Engineer / SDET · [Portfolio](https://parag-ithape.github.io) · [LinkedIn](https://www.linkedin.com/in/parag-ithape)
