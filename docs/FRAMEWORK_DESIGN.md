# ParaBank UI + API Test Automation Framework: Design Specification

> **Audience:** SDETs and developers who build and maintain the framework, and reviewers who want to understand its design.
> **Purpose:** a blueprint you can implement from. Every folder, class and config file has a stated responsibility, a code skeleton, and comments explaining *why* it exists.
> **System under test:** [ParaBank](https://parabank.parasoft.com/parabank), a demo online bank (accounts, transfers, bill pay, loans), tested through its **web UI** (Selenium + Cucumber BDD) and its **REST API** (REST Assured) in one Java project.

---

## Table of Contents
1. [Goals and Design Principles](#1-goals-and-design-principles)
2. [Technology Stack](#2-technology-stack)
3. [High-Level Architecture](#3-high-level-architecture)
4. [Project Folder Structure](#4-project-folder-structure)
5. [Build Configuration (pom.xml)](#5-build-configuration-pomxml)
6. [Configuration Management](#6-configuration-management)
7. [Driver Management](#7-driver-management)
8. [Locator Repository](#8-locator-repository)
9. [Message Repository](#9-message-repository)
10. [Test Data Management](#10-test-data-management)
11. [Core Utilities](#11-core-utilities)
12. [Page Object Model and Components](#12-page-object-model-and-components)
13. [BDD Layer (Cucumber on TestNG)](#13-bdd-layer-cucumber-on-testng)
14. [API Layer and Hybrid UI + API Testing](#14-api-layer-and-hybrid-ui--api-testing)
15. [Parallel Execution Strategy](#15-parallel-execution-strategy)
16. [Cross-Browser Strategy](#16-cross-browser-strategy)
17. [Allure Reporting](#17-allure-reporting)
18. [Logging](#18-logging)
19. [CI/CD (GitHub Actions)](#19-cicd-github-actions)
20. [Anti-Patterns This Design Avoids](#20-anti-patterns-this-design-avoids)
21. [Coding Standards and Conventions](#21-coding-standards-and-conventions)
22. [How to Run](#22-how-to-run)
23. [Implementation Roadmap](#23-implementation-roadmap)
- [Appendix A: .gitignore](#appendix-a-gitignore)
- [Appendix B: Exception hierarchy](#appendix-b-exception-hierarchy)

---

## 1. Goals and Design Principles

| # | Goal | How the design achieves it |
|---|------|----------------------------|
| G1 | **Business-readable tests** | Cucumber BDD: scenarios describe banking rules in plain English that a product owner can review. |
| G2 | **Two layers, one framework** | UI (Selenium) and API (REST Assured) share config, data, context and reporting. Hybrid scenarios combine them. |
| G3 | **Maintainability** | Strict layers (Core → Pages / API clients → Steps). Locators, messages, data and config each live in their own files. |
| G4 | **Sequential by default, parallel-safe when asked** | Scenarios run one after another in one browser (easy to watch and debug). `-DdpThreads=N` runs them in parallel; the `ThreadLocal` driver and context keep threads apart. No static mutable state. A new page object for every scenario. |
| G5 | **Fast and stable** | Test data is created through the API, not by clicking. Explicit waits only (no implicit wait, no `Thread.sleep`). Browser started only for `@ui` scenarios. |
| G6 | **Environment-agnostic** | One `baseUrl` switches between the public ParaBank site and a local/CI Docker instance. Values resolve: system property → environment variable → env file → defaults. |
| G7 | **Rich reporting** | Allure with Gherkin steps, screenshots, page source and API request/response attachments. |
| G8 | **Security** | No secrets in git. Credentials come from environment variables or GitHub Secrets and are masked in logs and reports. |
| G9 | **CI-ready** | Runs headless on Linux against a private ParaBank container and publishes the Allure report to GitHub Pages. |
| G10 | **Simple to read** | A flat layout anyone can navigate: `features/`, `pages/`, `stepdefinitions/`, `runner/`, `utils/`. The engine is a handful of classes in `utils/`. |

**Design principles**
- **Single responsibility:** a page *describes* a screen, an API client *describes* an endpoint, a step *orchestrates and asserts*, and the core *does the plumbing*.
- **Fail fast with clear messages:** a missing locator key, config key or data file throws a descriptive exception immediately, never a later `NullPointerException`.
- **Convention over configuration:** the locator file name equals the page name.
- **No magic strings in steps:** steps use page methods, API clients, `Messages.get(..)` and data objects.
- **Declarative Gherkin:** scenarios say *what* the customer does (`When they transfer 200.00 to savings`), never *how* (`When I click the Transfer button`).

---

## 2. Technology Stack

> ⚠️ Versions were checked on Maven Central at project start (Nov 2026) and are pinned in `pom.xml`. They are the latest stable releases **within the major line the design was written for**; newer majors (Cucumber 8, Allure 3, REST Assured 7, AssertJ 4, Log4j 3) are a deliberate upgrade, not a drop-in. **Exception: maven-surefire-plugin stays on 3.5.x**, because 3.6.0+ no longer supports TestNG suite XML files.

| Concern | Tool | Version (pinned) | Why |
|---|---|---|---|
| Language | Java (LTS) | 17 or 21 | Records, `switch` expressions, `var`, long-term support |
| Build | Maven | 3.9.x | Standard, CI-friendly, profiles |
| UI automation | Selenium WebDriver | 4.52.x | W3C protocol, Selenium Manager (no driver binaries) |
| BDD | Cucumber JVM + cucumber-testng + PicoContainer | 7.34.x | Gherkin scenarios, TestNG runner, dependency injection per scenario |
| Test engine | TestNG | 7.12.x | Parallel scenarios through DataProvider, suite XML |
| API testing | REST Assured + json-schema-validator | 5.5.x | Fluent HTTP client, JSON schema contract checks |
| Assertions | AssertJ | 3.27.x | Fluent, readable assertions with descriptions; soft assertions |
| Reporting | Allure (allure-cucumber7-jvm, allure-rest-assured) | 2.35.x | Rich HTML report with Gherkin steps and API attachments |
| Logging | Log4j2 | 2.26.x | Console and rolling run log |
| JSON data | Jackson Databind | 2.23.x | JSON ↔ POJO mapping for test data and API models |
| Fake data | Datafaker | 2.7.x | Unique, realistic customer data that stays safe in parallel |
| CI | GitHub Actions | n/a | Free for public repos; ParaBank runs as a service container |

---

## 3. High-Level Architecture

```
┌──────────────────────────────────────────────────────────────────────────────┐
│  EXECUTION   Maven → Surefire → testng.xml → TestRunner (Cucumber on TestNG) │
│              Tags select what runs: @ui  @api  @smoke  @regression           │
└───────────────┬──────────────────────────────────────────────────────────────┘
┌───────────────▼──────────────────────────────────────────────────────────────┐
│  features/*.feature        Gherkin scenarios in business language            │
│  stepdefinitions/          call pages or API clients, assert with AssertJ    │
│  runner/                   runner + hooks (browser only for @ui)             │
└───────────────┬───────────────────────────────────┬──────────────────────────┘
┌───────────────▼─────────────────────┐ ┌───────────▼──────────────────────────┐
│  pages/                             │ │  api/                   (phase 3)    │
│  LoginPage, TransferFundsPage …     │ │  AuthApi, AccountApi, TransferApi …  │
│  locators/<page>.properties         │ │  TestDataSetup (API-built state)     │
└───────────────┬─────────────────────┘ └───────────┬──────────────────────────┘
┌───────────────▼───────────────────────────────────▼──────────────────────────┐
│  utils/   the engine                                                         │
│  Config  Driver  Locators  Messages  Actions  TestData  ScenarioContext      │
│  Money  FrameworkException  SuiteListener                                    │
└───────────────┬──────────────────────────────────────────────────────────────┘
┌───────────────▼──────────────────────────────────────────────────────────────┐
│  ParaBank: web UI (Chrome | Firefox | Edge) and REST API (/services/bank)    │
│  Public demo site  ──or──  local / CI Docker container (parasoft/parabank)   │
└──────────────────────────────────────────────────────────────────────────────┘
```

**Dependency rule (enforce in code review):** stepdefinitions → (pages | api) → utils. `utils` never imports the other folders. Pages never import API clients or assertion libraries, and API clients never import pages.

---

## 4. Project Folder Structure

Flat and role-named, so anyone (technical or not) can find things. A folder exists only once it holds a file; the main folders below are kept from day one so the layout is visible.

```
parabank-ui-api-automation/
├── pom.xml                         # Build, dependencies, Surefire + Allure config
├── README.md                       # What is tested, how to run, report link
├── .gitignore                      # target/ and IDE files (NEVER pom.xml)
├── .editorconfig                   # Shared formatting rules
├── docs/
│   ├── FRAMEWORK_DESIGN.md         # This document
│   └── TEST_PLAN.md                # Test cases; their IDs are Cucumber tags (@TRF-02)
│
├── src/test/java/
│   ├── pages/                      # One class per screen (+ BasePage, shared widgets)
│   ├── stepdefinitions/            # Gherkin step code: call pages/API, assert with AssertJ
│   ├── runner/                     # CucumberTestRunner + Hooks (browser start/stop, screenshots)
│   ├── api/                        # (phase 3) REST clients, API models, TestDataSetup
│   └── utils/                      # The engine
│       ├── Config.java             #   settings: -D > env var > env-<env>.properties > config.properties
│       ├── Driver.java             #   start/get/quit the browser, one per thread
│       ├── Locators.java           #   reads locators/<page>.properties → Selenium By
│       ├── Messages.java           #   expected UI texts from messages_en.properties
│       ├── Actions.java            #   click/type/select + ALL explicit waits
│       ├── TestData.java           #   JSON test data, ${SECRET} placeholders, unique fake values
│       ├── ScenarioContext.java    #   share values between steps of one scenario
│       ├── Money.java              #   "$1,234.50" → BigDecimal
│       ├── FrameworkException.java #   the one error type, with clear messages
│       ├── SuiteListener.java      #   -DdpThreads + Allure environment/categories
│       └── LocatorIntegrityTest.java # checks every locator file before any browser starts
│
└── src/test/resources/
    ├── features/                   # *.feature files: the tests in plain English, tagged @ui / @api / @TRF-02
    ├── locators/                   # ONE FILE PER PAGE: common.properties, login.properties …
    ├── config.properties           # Defaults: browser, timeouts, headless…
    ├── env-public.properties       # baseUrl = public ParaBank site (default)
    ├── env-local.properties        # baseUrl = http://localhost:8080/parabank (Docker / CI)
    ├── messages_en.properties      # Expected UI messages
    ├── testdata/                   # (when needed) customers.json, payees.json
    ├── schemas/                    # (phase 3) JSON schemas for API contract checks
    ├── testng.xml                  # Runs LocatorIntegrityTest, then the scenarios
    ├── log4j2.xml                  # Console + run log under target/logs
    ├── allure.properties           # Allure results dir
    ├── allure-categories.json      # Failure groups on the Allure "Categories" tab
    └── META-INF/services/org.testng.ITestNGListener   # registers utils.SuiteListener
```

**Not in the repo on purpose:** driver `.exe` files (Selenium Manager downloads drivers), and `logs/`, `Reports/`, `test-output/` (all generated output lives under `target/`).

> **Naming note:** each folder is a short lower-case Java package named after its role (`pages`, `stepdefinitions`, `runner`, `utils`). That keeps the Java convention of lower-case packages without the deep `com/company/project/...` folder chain, which a single, self-contained test project doesn't need.

## 5. Build Configuration (pom.xml)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <groupId>com.parag</groupId>
  <artifactId>parabank-ui-api-automation</artifactId>
  <version>1.0.0-SNAPSHOT</version>
  <packaging>jar</packaging>

  <properties>
    <!-- ===== Java / encoding ===== -->
    <maven.compiler.release>17</maven.compiler.release>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>

    <!-- ===== Library versions (pin them, upgrade deliberately) ===== -->
    <selenium.version>4.52.0</selenium.version>
    <cucumber.version>7.34.9</cucumber.version>
    <testng.version>7.12.0</testng.version>
    <restassured.version>5.5.7</restassured.version>
    <allure.version>2.35.6</allure.version>
    <aspectj.version>1.9.27</aspectj.version>
    <assertj.version>3.27.7</assertj.version>
    <log4j.version>2.26.1</log4j.version>
    <jackson.version>2.23.0</jackson.version>
    <datafaker.version>2.7.0</datafaker.version>

    <!-- ===== Runtime defaults (all overridable with -D on the command line) ===== -->
    <env>public</env>
    <allure.results.directory>${project.build.directory}/allure-results</allure.results.directory>
  </properties>

  <dependencies>
    <!-- UI: Selenium includes Selenium Manager, so NO chromedriver.exe / WebDriverManager needed -->
    <dependency>
      <groupId>org.seleniumhq.selenium</groupId>
      <artifactId>selenium-java</artifactId>
      <version>${selenium.version}</version>
    </dependency>

    <!-- BDD: all Cucumber jars on the SAME version -->
    <dependency>
      <groupId>io.cucumber</groupId>
      <artifactId>cucumber-java</artifactId>
      <version>${cucumber.version}</version>
    </dependency>
    <dependency>
      <groupId>io.cucumber</groupId>
      <artifactId>cucumber-testng</artifactId>
      <version>${cucumber.version}</version>
    </dependency>
    <dependency> <!-- new step-class instances per scenario = parallel-safe instance fields -->
      <groupId>io.cucumber</groupId>
      <artifactId>cucumber-picocontainer</artifactId>
      <version>${cucumber.version}</version>
    </dependency>

    <!-- Test engine -->
    <dependency>
      <groupId>org.testng</groupId>
      <artifactId>testng</artifactId>
      <version>${testng.version}</version>
    </dependency>

    <!-- API -->
    <dependency>
      <groupId>io.rest-assured</groupId>
      <artifactId>rest-assured</artifactId>
      <version>${restassured.version}</version>
    </dependency>
    <dependency>
      <groupId>io.rest-assured</groupId>
      <artifactId>json-schema-validator</artifactId>
      <version>${restassured.version}</version>
    </dependency>

    <!-- Reporting: Cucumber adapter + REST Assured filter.
         Do NOT also add allure-testng: it would report every scenario twice. -->
    <dependency>
      <groupId>io.qameta.allure</groupId>
      <artifactId>allure-cucumber7-jvm</artifactId>
      <version>${allure.version}</version>
    </dependency>
    <dependency>
      <groupId>io.qameta.allure</groupId>
      <artifactId>allure-rest-assured</artifactId>
      <version>${allure.version}</version>
    </dependency>

    <!-- Assertions -->
    <dependency>
      <groupId>org.assertj</groupId>
      <artifactId>assertj-core</artifactId>
      <version>${assertj.version}</version>
    </dependency>

    <!-- Logging -->
    <dependency>
      <groupId>org.apache.logging.log4j</groupId>
      <artifactId>log4j-core</artifactId>
      <version>${log4j.version}</version>
    </dependency>
    <dependency> <!-- Routes Selenium's SLF4J logs into Log4j2 -->
      <groupId>org.apache.logging.log4j</groupId>
      <artifactId>log4j-slf4j2-impl</artifactId>
      <version>${log4j.version}</version>
    </dependency>

    <!-- Test data -->
    <dependency>
      <groupId>com.fasterxml.jackson.core</groupId>
      <artifactId>jackson-databind</artifactId>
      <version>${jackson.version}</version>
    </dependency>
    <dependency>
      <groupId>net.datafaker</groupId>
      <artifactId>datafaker</artifactId>
      <version>${datafaker.version}</version>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-compiler-plugin</artifactId>
        <version>3.16.0</version>
      </plugin>

      <!-- Surefire: runs the TestNG suite and attaches the AspectJ agent for Allure @Step.
           Stay on 3.5.x: 3.6.0+ runs TestNG on the JUnit Platform and no longer reads suite XML files. -->
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-surefire-plugin</artifactId>
        <version>3.5.6</version>
        <configuration>
          <suiteXmlFiles>
            <suiteXmlFile>src/test/resources/testng.xml</suiteXmlFile>
          </suiteXmlFiles>
          <!-- AspectJ weaver: without it, Allure @Step annotations on page methods are silently ignored -->
          <argLine>
            -javaagent:"${settings.localRepository}/org/aspectj/aspectjweaver/${aspectj.version}/aspectjweaver-${aspectj.version}.jar"
          </argLine>
          <systemPropertyVariables>
            <env>${env}</env>
            <allure.results.directory>${allure.results.directory}</allure.results.directory>
          </systemPropertyVariables>
          <!-- In CI, pass -Dmaven.test.failure.ignore=true so the report step still runs -->
        </configuration>
        <dependencies>
          <dependency>
            <groupId>org.aspectj</groupId>
            <artifactId>aspectjweaver</artifactId>
            <version>${aspectj.version}</version>
          </dependency>
        </dependencies>
      </plugin>

      <!-- Allure: mvn allure:serve (local) / mvn allure:report (CI) -->
      <plugin>
        <groupId>io.qameta.allure</groupId>
        <artifactId>allure-maven</artifactId>
        <version>2.18.0</version>
        <configuration>
          <reportVersion>2.46.2</reportVersion>
          <resultsDirectory>${allure.results.directory}</resultsDirectory>
        </configuration>
      </plugin>
    </plugins>
  </build>

  <profiles>
    <!-- mvn test -Plocal  → run against http://localhost:8080/parabank -->
    <profile><id>public</id><properties><env>public</env></properties></profile>
    <profile><id>local</id><properties><env>local</env></properties></profile>
  </profiles>
</project>
```

---

## 6. Configuration Management

### 6.1 Files

**`src/test/resources/config.properties`**: defaults shared by all environments
```properties
# ================= Browser / Execution =================
# chrome | firefox | edge
browser=chrome
# local | grid
execution.mode=local
headless=false
# true = one browser for the whole run (session cleared between scenarios); false = new browser per scenario
browser.reuse=true
window.width=1920
window.height=1080
grid.url=http://localhost:4444
# Page load strategy: normal | eager | none
page.load.strategy=normal

# ================= Timeouts (seconds) =================
# Explicit wait ONLY. There is intentionally no implicit wait key: mixing the two causes unpredictable waits.
timeout.explicit=20
timeout.pageload=60
timeout.script=30
timeout.polling.millis=250

# ================= Stability =================
click.retry.count=3

# ================= API =================
api.base.path=/services/bank
api.timeout.seconds=20
api.max.response.millis=3000

# ================= Reporting =================
screenshot.on.failure=true
attach.page.source.on.failure=true

# ================= Locale =================
messages.locale=en
```

**`src/test/resources/env-public.properties`**: the shared public demo site
```properties
baseUrl=https://parabank.parasoft.com/parabank
```

**`src/test/resources/env-local.properties`**: Docker on a laptop or a service container in CI
```properties
baseUrl=http://localhost:8080/parabank
```

> `baseUrl` is shared by the UI and the API layer, so switching environment changes both at once.

### 6.2 Resolution order (highest priority wins)

```
1. JVM system property        -Dbrowser=firefox        (CI / command line)
2. Environment variable       BROWSER=firefox          (key upper-cased, '.' → '_')
3. env-<env>.properties       env selected by -Denv=local or -Plocal (default public)
4. config.properties          defaults
→ missing everywhere  ⇒  FrameworkException("Missing config key: …")   (fail fast)
```

### 6.3 `utils/Config.java`

`utils/Config.java` loads `config.properties` and `env-<env>.properties` once from the classpath (works on any OS and in CI) and is read-only afterwards, so it is safe for parallel threads. Key names are constants on the class, so no code types a key by hand.

```java
String url   = Config.get(Config.BASE_URL);
int timeout  = Config.getInt(Config.TIMEOUT_EXPLICIT);
boolean head = Config.getBoolean(Config.HEADLESS);
String loc   = Config.get(Config.MESSAGES_LOCALE, "en");   // with a default
String env   = Config.env();                               // public | local
```

### 6.4 Secrets
- Passwords and tokens **never** go into git.
- Data files refer to them as `${PARABANK_PASSWORD}`, and `TestData.resolveSecrets` (§10) replaces the placeholder from an environment variable or system property at runtime.
- Customers created by the suite get **generated** passwords (`TestData.password()`), so most scenarios need no secret at all.
- In CI, any secret comes from GitHub Secrets. Locally, export it in your shell or IDE run configuration (**not** a committed `.env` file).
- Mask secrets: override `toString()` on models that hold passwords, never put a password in an Allure `@Step` title, and log API calls through `MaskingLogFilter` (§14).

---

## 7. Driver Management

`utils/Driver.java` is the only class that creates browsers.

```java
Driver.open();         // @Before hook of @ui scenarios: reuses the open browser, or starts one
Driver.get();          // pages get the current thread's browser
Driver.isStarted();    // hooks: is there a browser to screenshot?
Driver.release();      // @After hook: clears the session (browser.reuse=true) or closes the browser
Driver.quitAll();      // @AfterAll hook (and a JVM shutdown hook as safety net): closes every browser
```

**One browser for the whole run (`browser.reuse=true`, default).** Opening a browser per scenario is slow and makes runs hard to watch. Instead the browser opens once; after each scenario its cookies, local/session storage and extra tabs are cleared, so the next scenario starts logged out, exactly like a fresh browser for ParaBank. If the browser stopped responding, `open()` starts a new one; if clearing fails, the browser is closed and the next scenario gets a new one. `browser.reuse=false` restores a fresh browser per scenario.

**Per thread.** The browser is held in a `ThreadLocal`. With the default single thread that is one browser; with `-DdpThreads=N` each thread gets (and reuses) its own, so parallel scenario A never clicks in scenario B's browser.

- **Browsers:** `-Dbrowser=chrome|firefox|edge` (`Driver.Browser.from(..)` gives a clear error for typos like "chorme").
- **Where:** `execution.mode=local` or `grid` (with `grid.url`).
- **Options:** headless, window size and page-load strategy are set inside `Driver` and nowhere else, identically for local and Grid.
- **No driver binaries:** Selenium Manager (Selenium 4.6+) downloads the matching driver.
- **No implicit wait:** only page-load and script timeouts are set; all waiting is explicit, in `Actions`.
- **Quiet:** Selenium's harmless "Unable to find an exact match for CDP version" warning is hidden (no CDP features are used).

## 8. Locator Repository

Locators live in **separate files, one per page or component**, not in Java code. A UI change is then a one-line edit in a `.properties` file, and every locator is validated before the suite runs.

| Feature | Design |
|---|---|
| File layout | **1 file per page/component** (`locators/<page>.properties`) |
| Strategy | `id`, `name`, `css`, `xpath`, `linkText`, `partialLinkText`, `className`, `tagName` |
| Missing key | `FrameworkException("Missing locator key: login.username.input")` **at page construction** |
| Dynamic locators | `{0}`, `{1}` placeholders in the file |
| Validation | `LocatorIntegrityTest` checks that every file and value parses (runs before the scenarios) |

### 8.1 File format: `src/test/resources/locators/login.properties`

> The values below show the format. Confirm each one against the live ParaBank pages while building.

```properties
# =====================================================================
#  Page: Login (left panel on every public page)
#  File name == page name passed to Locators.forPage("login")
#  Format: <key>=<strategy>:<value>
#  Strategies: id | name | css | xpath | linkText | partialLinkText | className | tagName
#  Prefer: id > name > css > xpath (fastest and most stable first)
#  Dynamic parts: use {0}, {1}… and pass arguments from code.
#  NOTE: keys must not contain spaces, ':' or '='. Escape a literal backslash as '\\'.
# =====================================================================
username.input=name:username
password.input=name:password
login.button=css:input[value='Log In']
register.link=linkText:Register
error.message=css:#rightPanel p.error
```

**`locators/transfer-funds.properties`**
```properties
amount.input=id:amount
from.account.select=id:fromAccountId
to.account.select=id:toAccountId
transfer.button=css:input[value='Transfer']
result.title=css:#showResult h1.title
result.amount=id:amountResult
```

**`locators/common.properties`** (shared widgets)
```properties
page.title=css:#rightPanel h1.title
error.panel=css:#rightPanel .error
table.row.byText=xpath://table//tr[td[normalize-space()='{0}']]
menu.link.byText=linkText:{0}
```

### 8.2 `utils/Locators.java`

`utils/Locators.java` reads one page file and converts `strategy:value` to a Selenium `By`. Each file is read once, cached, and shared read-only by all threads.

```java
Locators login  = Locators.forPage("login");                 // locators/login.properties
By button       = login.by("login.button");                  // login.button=css:input[value='Log In']
By row          = Locators.forPage("common").by("table.row.byText", "Savings");   // fills {0}
```

- Splits on the **first** `:` only, because XPath values may contain more.
- Placeholders are filled literally by `Messages.fill(..)`, **not** `MessageFormat`, which treats single quotes as escapes and would silently break XPaths such as `//td[text()='{0}']`.
- Unknown strategy, missing `:`, empty value or missing key → `FrameworkException` naming the page and key.

### 8.3 Locator integrity test (catches broken locators before the scenarios run)
```java
// src/test/java/utils/LocatorIntegrityTest.java
// Parses EVERY key in EVERY locators/*.properties file. No browser needed, runs in under a second.
// Listed first in testng.xml, so a typo fails the build before any browser starts.
// Collects ALL bad entries and reports them in one AssertJ failure, instead of stopping at the first.
// Also fails when the folder or the file list is empty, so it can never pass vacuously.
public class LocatorIntegrityTest {
    @Test
    public void allLocatorFilesAreValid() throws IOException, URISyntaxException {
        URL dirUrl = getClass().getClassLoader().getResource("locators");
        assertThat(dirUrl).as("'locators' folder on the test classpath").isNotNull();
        List<Path> files;
        try (Stream<Path> listing = Files.list(Path.of(dirUrl.toURI()))) {
            files = listing.filter(f -> f.getFileName().toString().endsWith(".properties")).sorted().toList();
        }
        assertThat(files).as("locator files").isNotEmpty();

        List<String> problems = new ArrayList<>();
        for (Path file : files) {
            String page = file.getFileName().toString().replace(".properties", "");
            try {
                Locators repo = Locators.forPage(page);
                if (repo.keys().isEmpty()) problems.add(page + ": file has no locator entries");
                for (String key : repo.keys()) {
                    try { repo.by(key, "x", "y", "z"); }                 // throws on bad strategy/format/empty value
                    catch (FrameworkException | IllegalArgumentException e) { problems.add(e.getMessage()); }
                }
            } catch (FrameworkException e) { problems.add(e.getMessage()); }
        }
        assertThat(problems).as("invalid locator entries").isEmpty();
    }
}
```

> **Why not `@FindBy` / PageFactory?** PageFactory can't read locators from external files. Its lazy proxies also cause `StaleElementReferenceException` when the page re-renders, and the Selenium team no longer recommends it. Using `By` plus waits on every action is more robust.

---

## 9. Message Repository

Expected UI texts (confirmation titles, validation errors) live in **`messages_en.properties`**. Steps compare UI text against these keys, so a wording change in the app is a **one-line fix**.

> Confirm each text against the live app while building; the values below show the format.

**`src/test/resources/messages_en.properties`**
```properties
# ============ Login ============
login.error.invalid=The username and password could not be verified.
login.error.empty=Please enter a username and password.
# ============ Registration ============
register.success.title=Welcome {0}
# ============ Transfer ============
transfer.complete.title=Transfer Complete!
# ============ Bill pay ============
billpay.complete.title=Bill Payment Complete
billpay.validation.payee.required=Payee name is required.
# ============ Open account ============
openaccount.complete.title=Account Opened!
# ============ Loan ============
loan.approved.status=Approved
loan.denied.status=Denied
```

`utils/Messages.java` loads `messages_<messages.locale>.properties` as UTF-8, once:

```java
Messages.get("login.error.invalid")
Messages.get("register.success.title", username)   // fills {0}
```

---

## 10. Test Data Management

### 10.1 Strategy

| Data type | Format | Location | Example |
|---|---|---|---|
| Customer templates | JSON | `testdata/customers.json` | address, phone, SSN pattern |
| Bill-pay payees | JSON | `testdata/payees.json` | name, address, account number |
| Unique values (username, names) | Generated | `TestData` | `TestData.username()` → `bdd_mhz3k2a1_0y03` (max 20 chars) |
| Starting state (accounts, balances) | API | `api/TestDataSetup` | customer with checking 500.00 + empty savings |
| Secrets (if any) | Env vars | `${VAR}` placeholders | `${PARABANK_PASSWORD}` |

**Rules:**
- Scenarios **never share mutable data** across threads. Each scenario uses its own customer and accounts.
- **Prefer the API for setup.** Creating accounts and balances through the UI just to test a transfer is slow and flaky.
- **Don't rely on fixed demo users** (such as `john`/`demo`) on the public site; it is shared and reset. Register your own customer.

### 10.2 `customers.json` (template, no real passwords)
```json
[
  {
    "alias": "standard",
    "street": "221 Test Street",
    "city": "Pune",
    "state": "MH",
    "zipCode": "411001",
    "phone": "9000000000",
    "ssn": "123-45-6789"
  }
]
```

### 10.3 Model
```java
package pages;   // UI test-data records live next to the pages that use them

/** Immutable customer record. toString() masks the password, so it never appears in Allure parameters or logs. */
public record BankCustomer(String firstName, String lastName, String street, String city, String state,
                           String zipCode, String phone, String ssn, String username, String password) {
    @Override public String toString() {
        return "BankCustomer[username=" + username + ", name=" + firstName + " " + lastName + "]";
    }
}
```

### 10.4 `utils/TestData.java`

`utils/TestData.java` does three jobs:

```java
// 1. JSON files from the classpath → records/POJOs (unknown fields ignored)
List<CustomerTemplate> all = TestData.readList("testdata/customers.json", CustomerTemplate.class);
Payee payee = TestData.read("testdata/payee.json", Payee.class);

// 2. ${NAME} placeholders in that JSON are replaced from -DNAME or the NAME env variable
//    (fails fast naming the variable, never printing a value)
String text = TestData.resolveSecrets("password=${PARABANK_PASSWORD}");

// 3. Unique, parallel-safe generated values
TestData.username();    // "bdd_" + millis + thread id + counter, base 36: unique even in the same millisecond.
                        // At most 20 characters: ParaBank rejects longer usernames with a misleading
                        // "This username already exists" (verified 2026-11-14).
TestData.password();    // "Pw!" + 12 chars with upper, lower and digit (Datafaker text rule)
TestData.firstName();  TestData.lastName();
```

---

## 11. Core Utilities

### 11.1 `utils/Actions.java`: every interaction and every wait
Pages never call `driver.findElement(..).click()`. They call `Actions`, so waiting, retries and logging live in one place. One instance per page object (so per scenario thread).

| Group | Methods |
|---|---|
| Interact | `click(by)` (retries on stale/intercepted, `click.retry.count`), `type(by, text)`, `typeSecret(by, secret)` (logs `******`), `selectByText`, `selectByValue` |
| Read | `text(by)`, `attribute(by, name)` (DOM attribute), `value(by)` (live input value), `optionTexts(by)`, `isDisplayed(by)` (never throws) |
| Wait | `waitVisible`, `waitClickable`, `waitPresent`, `waitAllVisible`, `waitInvisible`, `waitText`, `waitUrlContains`, `waitOptionsLoaded` (ParaBank fills account dropdowns late), `waitPageReady`, `waitUntil(condition, description)` |
| Last resort | `jsClick(by)`, `scrollIntoView(by)`: say why at the call site |

**Waiting rules:** explicit waits only (`timeout.explicit`, polling `timeout.polling.millis`, stale elements ignored while waiting). No implicit waits (mixing them gives unpredictable wait times) and no `Thread.sleep` (slow and flaky). `waitUntil` builds a fresh wait each time, because `withMessage()` changes the wait it is called on and the message would otherwise stick to later timeouts. `WebElement.getAttribute` is deprecated since Selenium 4.27, hence `getDomAttribute` / `getDomProperty`.

### 11.2 `utils/Money.java`
Bank amounts are always `BigDecimal`, never `double` (which loses cents). `Money.parse("$1,234.50")` → `1234.50`, `Money.parse("-$20.00")` → `-20.00`; unreadable text throws `FrameworkException` quoting it.

---

## 12. Page Object Model and Components

### 12.1 Rules
1. A page **exposes business actions** (`transfer(amount, from, to)`), not raw clicks.
2. A method that navigates **returns the next page object** (fluent chaining).
3. Pages contain **no assertions**. They return values or state (`getResultTitle()`, `isLoaded()`), and steps assert.
4. Locators are **private** fields resolved in the constructor from the page's locator file, so a missing key fails immediately.
5. Repeated widgets (left menu, accounts table) are **components**, written once.
6. `@Step` (Allure) goes on page methods so the report reads like a business flow.

### 12.2 `BasePage.java`
```java
package pages;

/**
 * Parent of all pages. It gives every page:
 *  - the current thread's driver (from Driver.get(), never a copied static field)
 *  - Actions (interactions + waits)
 *  - its own Locators (file name = pageName)
 *  - the shared "common" locators
 */
public abstract class BasePage {

    protected final WebDriver driver;
    protected final Actions actions;
    protected final Locators loc;
    protected final Locators common = Locators.forPage("common");
    protected final Logger log = LogManager.getLogger(getClass());

    protected BasePage(String pageName) {
        this.driver = Driver.get();
        this.actions = new Actions(driver);
        this.loc = Locators.forPage(pageName);
    }

    /** Each page defines what "loaded" means (a unique element). */
    public abstract boolean isLoaded();

    /** Generic page-load check that fails with a clear message. */
    @SuppressWarnings("unchecked")
    protected <T extends BasePage> T waitUntilLoaded() {
        actions.waitUntil(d -> isLoaded(), getClass().getSimpleName() + " did not load");
        return (T) this;
    }

    /** Opens a ParaBank page by path ("/index.htm"), so no page builds URLs itself. */
    protected void openPath(String path) { driver.get(Config.get(Config.BASE_URL) + path); }

    public String getPageTitle() { return actions.text(common.by("page.title")); }
}
```

### 12.3 `LoginPage.java`
```java
package pages;

public class LoginPage extends BasePage {

    // Resolved at construction, so a missing locator key fails immediately with the page + key name
    private final By username = loc.by("username.input");
    private final By password = loc.by("password.input");
    private final By loginBtn = loc.by("login.button");
    private final By error    = loc.by("error.message");

    public LoginPage() { super("login"); }

    @Step("Open ParaBank")
    public LoginPage open() {
        openPath("/index.htm");
        return waitUntilLoaded();
    }

    /** Happy path: returns the next page. The password is never in the step title or logs. */
    @Step("Log in as '{customer.username}'")
    public AccountsOverviewPage loginAs(BankCustomer customer) {
        actions.type(username, customer.username());
        actions.typeSecret(password, customer.password());
        actions.click(loginBtn);
        return new AccountsOverviewPage().waitUntilLoaded();
    }

    /** Negative path: stays on the login page. */
    @Step("Attempt login with invalid credentials")
    public LoginPage attemptLogin(String user, String pass) {
        actions.type(username, user);
        actions.typeSecret(password, pass);
        actions.click(loginBtn);
        return this;
    }

    public String getErrorMessage() { return actions.text(error); }

    @Override public boolean isLoaded() { return actions.isDisplayed(loginBtn); }
}
```

### 12.4 `TransferFundsPage.java`
```java
package pages;

public class TransferFundsPage extends BasePage {

    private final By amount      = loc.by("amount.input");
    private final By fromAccount = loc.by("from.account.select");
    private final By toAccount   = loc.by("to.account.select");
    private final By transferBtn = loc.by("transfer.button");
    private final By resultTitle = loc.by("result.title");

    public TransferFundsPage() { super("transfer-funds"); }

    @Step("Transfer {value} from account {fromId} to account {toId}")
    public TransferFundsPage transfer(BigDecimal value, long fromId, long toId) {
        actions.waitOptionsLoaded(fromAccount);          // account lists are filled after page load
        actions.type(amount, value.toPlainString());
        actions.selectByValue(fromAccount, String.valueOf(fromId));
        actions.selectByValue(toAccount, String.valueOf(toId));
        actions.click(transferBtn);
        return this;
    }

    public String getResultTitle() { return actions.text(resultTitle); }

    @Override public boolean isLoaded() { return actions.isDisplayed(transferBtn); }
}
```

### 12.5 Component example: `AccountsTableComponent.java`
```java
package pages;   // shared widgets live next to the pages

/** The accounts overview table, written once and used by every page that lists accounts. */
public class AccountsTableComponent extends BasePage {   // components extend BasePage too (one parent, no BaseComponent)

    public AccountsTableComponent() { super("accounts-overview"); }

    /** Balance shown in the UI for one account, as BigDecimal (never double). */
    public BigDecimal balanceOf(long accountId) {
        String text = actions.text(loc.by("balance.byAccountId", accountId));   // dynamic locator {0}
        return Money.parse(text);
    }

    public List<Long> accountIds() { /* reads the account-number links in the table */ }
}
```

---

## 13. BDD Layer (Cucumber on TestNG)

Cucumber is the **primary way tests are written**. Pages, API clients, locators, messages, data, drivers and Allure all sit underneath it.

```
src/test/resources/features/*.feature   # scenarios, grouped by tags (@ui, @api), not by folders
src/test/java/stepdefinitions/           # LoginSteps, TransferSteps, TransferApiSteps …
src/test/java/runner/                    # CucumberTestRunner.java, Hooks.java
```

### 13.1 Runner
```java
@CucumberOptions(
    features = "src/test/resources/features",
    glue = {"stepdefinitions", "runner"},
    plugin = {"runner.StepLogger",                                 // readable run log (section 18)
              "io.qameta.allure.cucumber7jvm.AllureCucumber7Jvm",   // Allure for BDD
              "rerun:target/rerun.txt"},                           // failed scenarios, for a re-run
    monochrome = true
    // NO hardcoded tags here. Pass them with: -Dcucumber.filter.tags="@smoke and not @wip"
)
public class CucumberTestRunner extends AbstractTestNGCucumberTests {
    /** parallel = true → scenarios run concurrently; pool size = data-provider-thread-count (-DdpThreads=4). */
    @Override
    @DataProvider(parallel = true)
    public Object[][] scenarios() { return super.scenarios(); }
}
```

**`src/test/resources/testng.xml`**
```xml
<!DOCTYPE suite SYSTEM "https://testng.org/testng-1.0.dtd">
<!-- data-provider-thread-count = how many scenarios run at once. Override with -DdpThreads=N. -->
<suite name="ParaBank" data-provider-thread-count="1">   <!-- one at a time; -DdpThreads=N for parallel -->
  <test name="Framework checks" preserve-order="true">
    <classes><class name="utils.LocatorIntegrityTest"/></classes>
  </test>
  <test name="Scenarios">
    <classes><class name="runner.CucumberTestRunner"/></classes>
  </test>
</suite>
```
> Scenarios run one after another by default, in one browser (§7). `-DdpThreads=N` runs N at a time, each thread with its own browser (§15).

### 13.2 Hooks: browser only for `@ui`, cleanup for everything
```java
public class Hooks {

    @Before(order = 0)                                   // every scenario: tag log-file lines with its name
    public void nameLogLines(Scenario scenario) { ThreadContext.put("scenario", scenario.getName().replaceAll("\\W+", "_")); }

    /** API-only scenarios never open a browser, so they stay fast. */
    @Before(value = "@ui", order = 1)
    public void openBrowser() { ThreadContext.put("browser", Config.get(Config.BROWSER)); Driver.open(); }

    /** Evidence is attached BEFORE the session is cleared: afterwards the failing page is gone. */
    @After(value = "@ui", order = 1)                     // @After hooks run highest order first
    public void releaseBrowser(Scenario scenario) {
        try {
            if (scenario.isFailed() && Driver.isStarted()) {
                if (Config.getBoolean(Config.SCREENSHOT_ON_FAILURE)) {
                    byte[] png = ((TakesScreenshot) Driver.get()).getScreenshotAs(OutputType.BYTES);
                    scenario.attach(png, "image/png", "Failure screenshot");
                }
                if (Config.getBoolean(Config.ATTACH_PAGE_SOURCE_ON_FAILURE)) AllureAttachments.pageSource();
                AllureAttachments.browserConsoleLogs();
            }
        } finally {
            Driver.release();                            // clear session (or close), even if attaching failed
        }
    }

    @After(order = 0)                                    // every scenario, last: nothing leaks to the next one
    public void clearContext() { ScenarioContext.clear(); ThreadContext.clearAll(); }

    @AfterAll                                            // after the last scenario
    public static void closeBrowsers() { Driver.quitAll(); }
}
```

### 13.3 `utils/ScenarioContext.java`

`utils/ScenarioContext.java` passes data between the steps of ONE scenario (e.g. an account id created in a Given step and checked in a Then step). It is a per-thread map, cleared by the `@After` hook. Keys are constants on the class; add a new constant instead of typing free text.

```java
ScenarioContext.put(ScenarioContext.CHECKING_ACCOUNT_ID, id);
long id = ScenarioContext.get(ScenarioContext.CHECKING_ACCOUNT_ID);   // fails naming the key if missing
ScenarioContext.clear();                                              // @After hook
```
Keys today: `CUSTOMER`, `CUSTOMER_ID`, `CHECKING_ACCOUNT_ID`, `SAVINGS_ACCOUNT_ID`, `LAST_RESPONSE`.

### 13.4 Steps stay thin: they call pages or API clients, then assert
```java
public class TransferUiSteps {
    private TransferFundsPage transferPage;   // PicoContainer creates new step instances per scenario, so instance fields are safe

    @When("they transfer {bigdecimal} from checking to savings in the browser")
    public void transferInBrowser(BigDecimal amount) {
        transferPage = new LeftMenuComponent().openTransferFunds()
                .transfer(amount,
                          ScenarioContext.get(ScenarioContext.CHECKING_ACCOUNT_ID),
                          ScenarioContext.get(ScenarioContext.SAVINGS_ACCOUNT_ID));
    }

    @Then("the confirmation page shows the transfer is complete")
    public void confirmationShown() {
        assertThat(transferPage.getResultTitle())
                .as("Transfer confirmation title")
                .isEqualTo(Messages.get("transfer.complete.title"));
    }
}
```

### 13.5 Feature examples

**`features/login.feature`** (as built in phase 2; `@regression` on the Feature, `@smoke` on P1 cases, test-plan ID on every scenario)
```gherkin
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
```
Steps read as business sentences; the expected texts behind them (`login.error.invalid` …) stay in `messages_en.properties`, never in the feature.

**Customers for UI scenarios:** ParaBank has no registration API, so `Given a registered customer` registers through the UI once per thread (cached in a `ThreadLocal`), logs out, and reuses that customer in later scenarios on the same thread. Registration scenarios generate their own new customer.

**`features/bill-pay.feature`**
```gherkin
@ui
Feature: Bill pay

  Background:
    Given a customer with a checking account holding 500.00
    And the customer is logged in

  @smoke
  Scenario: Customer pays a bill from checking
    When they pay 75.00 to payee "Electric Company" from checking
    Then the bill payment is confirmed
    And the checking balance is 425.00

  @regression
  Scenario: Bill pay requires a payee name
    When they submit a bill payment without a payee name
    Then the validation message "billpay.validation.payee.required" is shown
```

### 13.6 Gherkin guidelines
- Write **declarative** steps that describe business intent (`Given a customer with a checking account holding 500.00`), not imperative clicks.
- Use `Background` for shared setup such as login.
- Keep each scenario to one behaviour; use `Scenario Outline` for data variations.
- Delete scenarios instead of commenting them out; git keeps the history.
- If ParaBank behaves differently from the business rule a scenario expects (for example, it accepts an overdraft), **don't change the expectation to match**. Tag the scenario `@knownIssue`, link the finding in the README, and exclude it from the default run.

### 13.7 Tags
| Tag | Purpose |
|---|---|
| `@ui` | Needs a browser (the hook starts one) |
| `@api` | Uses the API (alone, or with `@ui` for hybrid) |
| `@smoke` | Critical paths, under 5 minutes, run on every push |
| `@regression` | Full suite, nightly |
| `@wip` | Work in progress, excluded by default |
| `@knownIssue` | App behaviour differs from the expected rule; documented, excluded by default |
| `@TRF-02`, `@LOG-01` … | Test-case ID from `docs/TEST_PLAN.md`: every scenario carries its ID, so each requirement traces to a test and back |

---

## 14. API Layer and Hybrid UI + API Testing

API testing is a **first-class layer**, not a helper. It serves three purposes:
1. **API scenarios** test banking rules directly, without a browser.
2. **Fast setup:** UI scenarios get their starting state (accounts, balances) from the API in seconds.
3. **Independent verification:** after a UI action, the API confirms the result, proving the UI isn't just *showing* the right number.

> Confirm endpoint paths against the live WADL at `<baseUrl>/services/bank?_wadl` before coding.

### 14.1 Goals

| # | Goal | How |
|---|------|-----|
| A1 | API scenarios run **without a browser** | The driver hook runs only for `@ui` (§13.2) |
| A2 | One place per endpoint | `*Api` client classes; steps never build requests themselves |
| A3 | Fast, stable UI scenarios | Starting state is created through the API, not by clicking |
| A4 | Prove the UI tells the truth | Hybrid scenarios verify UI actions through the API |
| A5 | Contract safety | JSON schema validation on key responses |
| A6 | Same security rules as the UI | Secrets from env vars; passwords masked in logs and Allure |

### 14.2 `RequestSpecFactory.java`
```java
package api;

/** One shared request spec. ParaBank returns XML by default, so JSON is requested explicitly. */
public final class RequestSpecFactory {
    private RequestSpecFactory() { }

    public static RequestSpecification base() {
        return new RequestSpecBuilder()
                .setBaseUri(Config.get("baseUrl"))
                .setBasePath(Config.get("api.base.path"))
                .setAccept(ContentType.JSON)
                .setConfig(RestAssuredConfig.config().httpClient(HttpClientConfig.httpClientConfig()
                        .setParam("http.connection.timeout", ApiConfig.timeoutMillis())
                        .setParam("http.socket.timeout", ApiConfig.timeoutMillis())))
                .addFilter(new MaskingLogFilter())          // never prints real passwords
                .addFilter(new AllureRestAssured())         // request/response attached to the Allure step
                .build();
    }
}
```

### 14.3 Client example: `TransferApi.java`
```java
package api;

/** One method per endpoint. Returns the raw Response so callers can assert status and body. */
public final class TransferApi {

    public Response transfer(long fromAccountId, long toAccountId, BigDecimal amount) {
        return given().spec(RequestSpecFactory.base())
                .queryParam("fromAccountId", fromAccountId)
                .queryParam("toAccountId", toAccountId)
                .queryParam("amount", amount)
            .when()
                .post("/transfer");
    }
}
```

ParaBank endpoints used by this suite (verify against the WADL):

| Client | Method | Path |
|---|---|---|
| AuthApi | GET | `/login/{username}/{password}` |
| CustomerApi | GET | `/customers/{customerId}` · `/customers/{customerId}/accounts` |
| AccountApi | POST | `/createAccount?customerId=&newAccountType=&fromAccountId=` (0 = checking, 1 = savings) |
| AccountApi | GET | `/accounts/{accountId}` · `/accounts/{accountId}/transactions` |
| TransferApi | POST | `/transfer?fromAccountId=&toAccountId=&amount=` · `/deposit` · `/withdraw` |
| LoanApi | POST | `/requestLoan?customerId=&amount=&downPayment=&fromAccountId=` |

### 14.4 Test data through the API: `TestDataSetup.java`
```java
package api;

/**
 * Builds the starting state for a scenario in seconds instead of minutes of clicking.
 * ParaBank has no REST endpoint for registration, so each thread registers ONE customer through the UI
 * (or a short-lived browser session for API-only runs) and caches it. Accounts and balances
 * are then created per scenario through the API.
 */
public final class TestDataSetup {

    private static final ThreadLocal<BankCustomer> CUSTOMER_PER_THREAD = new ThreadLocal<>();

    public void customerWithChecking(BigDecimal balance) {
        BankCustomer customer = customerForThisThread();
        long customerId = new AuthApi().login(customer).id();
        long checkingId = new AccountApi().createChecking(customerId, /* funded to */ balance);
        ScenarioContext.put(ScenarioContext.CUSTOMER, customer);
        ScenarioContext.put(ScenarioContext.CUSTOMER_ID, customerId);
        ScenarioContext.put(ScenarioContext.CHECKING_ACCOUNT_ID, checkingId);
    }

    public void addSavingsAccount() { /* createAccount type 1, store SAVINGS_ACCOUNT_ID */ }

    private BankCustomer customerForThisThread() { /* register once per thread, then reuse */ }
}
```

### 14.5 `MaskingLogFilter.java`
```java
package api;

/** Logs every API call, with the password path segment and password parameters masked. */
public class MaskingLogFilter implements Filter {
    private static final Logger LOG = LogManager.getLogger(MaskingLogFilter.class);
    private static final Pattern LOGIN_PATH = Pattern.compile("(/login/[^/]+/)[^/?]+");

    @Override
    public Response filter(FilterableRequestSpecification req, FilterableResponseSpecification res, FilterContext ctx) {
        Response response = ctx.next(req, res);
        String uri = LOGIN_PATH.matcher(req.getURI()).replaceAll("$1******");
        LOG.info("{} {} → {} ({} ms)", req.getMethod(), uri, response.statusCode(), response.time());
        return response;
    }
}
```

### 14.6 Scenario styles

**Pure API (business rule, no browser):**
```gherkin
@api @regression
Scenario: Transfer moves money between the customer's own accounts
  Given a customer with a checking account holding 500.00 and an empty savings account
  When they transfer 200.00 from checking to savings through the API
  Then the API reports a checking balance of 300.00 and a savings balance of 200.00
```

**Business rule check (tag `@knownIssue` if ParaBank doesn't enforce it):**
```gherkin
@api @regression
Scenario: Transfer above the available balance is rejected
  Given a customer with a checking account holding 500.00 and an empty savings account
  When they transfer 600.00 from checking to savings through the API
  Then the transfer is rejected
  And the checking balance is still 500.00
```

**Hybrid (API setup, UI action, API verification):**
```gherkin
@ui @api @smoke
Scenario: Transfer in the browser updates both balances
  Given a customer with a checking account holding 500.00 and an empty savings account
  And the customer is logged in
  When they transfer 200.00 from checking to savings in the browser
  Then the confirmation page shows the transfer is complete
  And the API reports a checking balance of 300.00 and a savings balance of 200.00
```

**Rules:**
- Steps stay **declarative**, as in §13.6.
- A step calls either a page or an API client, then asserts with AssertJ. No HTTP or WebDriver code in steps.
- Shared `Given` steps (data setup) live in `steps/common`, so UI, API and hybrid features reuse them.

### 14.7 Assertions and contract checks
```java
response.then()
        .statusCode(200)
        .body(matchesJsonSchemaInClasspath("schemas/account.json"))
        .time(lessThan((long) Config.getInt("api.max.response.millis")));
Account account = response.as(Account.class);
assertThat(account.balance()).as("Checking balance after transfer").isEqualByComparingTo("300.00");
```

Money is compared with `BigDecimal.isEqualByComparingTo`, never with `double`, so `300.0` and `300.00` match and rounding never causes false failures.

---

## 15. Parallel Execution Strategy

### 15.1 Sequential by default, parallel on request

- **Default: one at a time.** `data-provider-thread-count="1"` in `testng.xml`: scenarios run in order in a single browser, which is easiest to watch and debug.
- **Parallel on request:** `-DdpThreads=N`. `CucumberTestRunner.scenarios()` is a `@DataProvider(parallel = true)`, so each scenario is one invocation on a pool of N threads. Each thread has its own browser (reused across its scenarios), its own `ScenarioContext`, and new step-class instances per scenario (PicoContainer).

`utils/SuiteListener.java` (registered in `META-INF/services`) applies `-DdpThreads=N` to every suite before it starts, rejects values that are not positive whole numbers, and logs how the run is set up.

### 15.2 Thread-safety checklist (mandatory in code review)
- [ ] No `static` WebDriver, WebElement, page or test-data fields (`static final` constants are fine).
- [ ] The driver is only obtained through `Driver.get()`.
- [ ] Page objects are created **inside** steps, never shared between scenarios.
- [ ] Shared runtime data goes in `ScenarioContext` (ThreadLocal), never in static maps.
- [ ] Every scenario uses **its own accounts**; no two scenarios change the same account.
- [ ] Registered customers are cached **per thread** (`ThreadLocal`), never shared across threads.

### 15.3 Thread-count sizing
- Laptop: roughly **CPU cores − 1** browsers (each Chrome uses about 300–500 MB of RAM).
- Public ParaBank site: keep it **low (2–3)**. It's a shared demo; don't load it. `testng.xml` defaults to 1.
- Local or CI container: 4 is a good default. Increase step by step. If failures rise with thread count, look for shared state or environment capacity limits, not "flaky tests".

---

## 16. Cross-Browser Strategy

- The browser comes from `-Dbrowser=chrome|firefox|edge` (default `chrome`).
- **CI runs a browser matrix**: one job per browser (§19), so each browser has its own report and failures are easy to separate.
- **Coding rules:**
  - Browser-specific flags live only in `Driver`, never in pages.
  - Avoid CDP-only features (network interception) in scenarios that must run on Firefox.
  - Prefer `id`/`name`/CSS locators; use XPath only when the DOM gives nothing better.
- **Selenium Grid (optional, later):** `execution.mode=grid` with `grid.url` already works through `Driver.open()`. A `docker-compose` Grid file can be added when the suite needs more parallel browsers than one machine can run.

---

## 17. Allure Reporting

### 17.1 `src/test/resources/allure.properties`
```properties
allure.results.directory=target/allure-results
```

### 17.2 What appears in the report
| Source | Shows up as |
|---|---|
| Gherkin steps (AllureCucumber7Jvm plugin) | The scenario's steps, with pass/fail per step |
| `@Step` on page methods | Nested business steps inside each Gherkin step |
| `AllureRestAssured` filter | Request and response of every API call, attached to the step |
| `Hooks` on failure | Screenshot, page source, browser console log |
| Feature folders + tags | Behaviours tab grouped by feature; filter by `@smoke`, `@api`… |

### 17.3 `utils/AllureAttachments.java` (phase 2)
```java
package utils;

/** Every attachment helper in one place. All methods are safe to call even if the driver is broken. */
public final class AllureAttachments {
    private AllureAttachments() { }

    public static void pageSource() {
        try { Allure.addAttachment("Page source", "text/html", Driver.get().getPageSource(), "html"); }
        catch (Exception e) { LogManager.getLogger().warn("Page source failed: {}", e.getMessage()); }
    }

    /** Browser console errors (Chromium browsers only; skipped silently elsewhere). */
    public static void browserConsoleLogs() {
        try {
            String logs = Driver.get().manage().logs().get(LogType.BROWSER).getAll()
                    .stream().map(Object::toString).collect(Collectors.joining("\n"));
            if (!logs.isBlank()) Allure.addAttachment("Browser console", "text/plain", logs);
        } catch (Exception ignored) { /* not supported by this browser */ }
    }
}
```

### 17.4 Environment widget + failure categories
`utils/SuiteListener.java`, when the suite starts, writes `environment.properties` (env, base URL, browser, headless, Java, OS) into the Allure results folder and copies `allure-categories.json` there as `categories.json`. A reporting problem is logged as a warning and never fails the run.

**`src/test/resources/allure-categories.json`**: groups failures by root cause on the Allure "Categories" tab
```json
[
  { "name": "Product defects (assertion failed)", "matchedStatuses": ["failed"], "messageRegex": ".*(expected|Expecting).*" },
  { "name": "Locator / element issues",           "matchedStatuses": ["broken"], "traceRegex": ".*(NoSuchElementException|StaleElementReferenceException|Missing locator key|Bad locator format|Unknown strategy|Locator file not found).*" },
  { "name": "Timeouts / environment slowness",    "matchedStatuses": ["broken"], "traceRegex": ".*TimeoutException.*" },
  { "name": "API / network",                      "matchedStatuses": ["broken"], "traceRegex": ".*(ConnectException|SocketTimeoutException|HttpHostConnectException).*" },
  { "name": "Test data / config problems",        "matchedStatuses": ["broken"], "traceRegex": ".*FrameworkException.*" },
  { "name": "Skipped",                            "matchedStatuses": ["skipped"] }
]
```

**`src/test/resources/META-INF/services/org.testng.ITestNGListener`** (auto-registered, no suite XML entries needed)
```
utils.SuiteListener
```

### 17.5 Generating the report
```bash
mvn allure:serve      # local: builds the report and opens it in a browser
mvn allure:report     # CI: writes static HTML to target/site/allure-maven-plugin
```

---

## 18. Logging

The console tells the story of the run in plain language, so anyone (tester, developer, manager, client) can follow it. The log file keeps the technical detail.

**Console** (lines from a real run):
```
23:01:18 SCENARIO  [LOG-01] Registered customer logs in
23:01:18             Starting Chrome (visible, window 1920x1080)
23:01:23 STEP      Given a registered customer
23:01:25             Registering a new customer "bdd_mwgomydj_0y01" for this test run
23:01:25             Open https://parabank.parasoft.com/parabank/register.htm
23:01:28             Enter "Jasmine" in first name field (Register page)
23:01:31             Enter ******** in password field (Register page)
23:01:32             Click register button (Register page)
23:01:33 STEP      When they log in with their username and password
23:01:36 RESULT    PASSED   [LOG-01] Registered customer logs in  (18.1 s)
...
23:05:05             Leave username field (Login page) empty
23:09:23 FAILED    in step "their accounts overview is shown": [Title of the page shown after login] expected: "X" but was: "Accounts Overview"
23:08:35 SUMMARY   8 scenarios: 8 passed, 0 failed, 0 skipped  (total time 42.9 s)
```

| Part | Where it comes from |
|---|---|
| `SCENARIO`, `STEP`, `FAILED`, `RESULT`, `SUMMARY` | `runner/StepLogger.java`, a Cucumber plugin. The test-plan ID comes from the scenario's tag; a failure is reduced to one readable line. |
| Indented action lines | `Actions` and pages. Elements are named from their locator key and page (`login.button` on `login` → "login button (Login page)", `firstName.input` → "first name field"), never by raw selector. Passwords always show as `********`. |
| Browser lines | `Driver`: starting, reusing, session cleared, closed. |

INFO lines show no level; warnings and errors start with `WARN:` / `ERROR:`. **`target/logs/run.log`** has every line with time in ms, thread, scenario, browser, level and class, rolled at 20 MB (under `target/`, so never committed). Both are configured in `src/test/resources/log4j2.xml`.

Raw selectors still appear where they help debugging: in timeout and "element not found" errors (e.g. `waiting for visibility of login button (Login page) [By.cssSelector: input[value='Log In']]`).

**Logging rules:** `log.info` for what a reader should see (actions, data used), `log.debug` for technical detail, `log.warn` for retries and fallbacks, `log.error` for failures. **Never** log secrets (use `typeSecret`). **Never** use `System.out.println`.

## 19. CI/CD (GitHub Actions)

### 19.1 `.github/workflows/tests.yml`
```yaml
name: ParaBank Tests
on:
  push:
    branches: [main]
  pull_request:
  schedule: [{ cron: "30 20 * * *" }]           # nightly regression
  workflow_dispatch:
    inputs:
      tags: { description: "Cucumber tag expression", default: "not @wip and not @knownIssue" }

jobs:
  test:
    runs-on: ubuntu-latest
    strategy:
      fail-fast: false
      matrix:
        browser: [chrome, firefox]               # one job per browser
    services:
      parabank:                                  # a private bank for every run: no shared data, no outages
        image: parasoft/parabank
        ports: ["8080:8080"]
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: "17", cache: maven }

      - name: Wait for ParaBank
        run: |
          for i in $(seq 1 30); do
            curl -sf http://localhost:8080/parabank/index.htm && exit 0; sleep 5
          done; exit 1

      - name: Run tests
        run: >
          mvn -B clean test -Plocal
          -Dbrowser=${{ matrix.browser }} -Dheadless=true -DdpThreads=4
          -Dcucumber.filter.tags="${{ github.event.inputs.tags || (github.event_name == 'schedule' && 'not @wip and not @knownIssue' || '@smoke') }}"

      - name: Build Allure report
        if: always()
        run: mvn -B allure:report

      - uses: actions/upload-artifact@v4
        if: always()
        with:
          name: allure-report-${{ matrix.browser }}
          path: target/site/allure-maven-plugin
  # A follow-up job publishes the Chrome report to GitHub Pages (actions/upload-pages-artifact + actions/deploy-pages).
  # That Pages URL is the public report link in the README (showcase item S7, §23).
```

### 19.2 Quality gates
- **Smoke must be 100% green** on every push and pull request; regression runs nightly.
- The job fails when any scenario fails (no `failure.ignore` in CI), but the report steps still run (`if: always()`).
- `LocatorIntegrityTest` runs first, so a broken locator file fails the build in seconds, before any browser starts.
- Optional: post pass/fail counts and the report link to Teams or Slack from a final step.

---

## 20. Anti-Patterns This Design Avoids

| # | Anti-pattern | Risk | How this design avoids it |
|---|---|---|---|
| 1 | Passwords or tokens committed to git | Security breach | `${SECRET}` placeholders, generated customer passwords, GitHub Secrets (§6.4, §10) |
| 2 | Driver binaries (`chromedriver.exe`) in the repo; Windows-only paths | Breaks on browser updates and on Linux CI | Selenium Manager + classpath loading (§6.3, §7) |
| 3 | Browser started inside a Gherkin step ("Given I launch the app") | Order-dependent failures; NPE in `@After` | `Driver.open()` in the `@ui` `@Before` hook (§13.2) |
| 4 | `static WebDriver` | Parallel scenarios corrupt each other | `ThreadLocal` in `Driver` (§7) |
| 5 | Mixed test engines / mismatched Cucumber versions | Classpath conflicts, wrong runner | TestNG only; one `${cucumber.version}` (§5) |
| 6 | One giant locator file, or locators hard-coded in Java | Fragile, hard to maintain | One locator file per page, multi-strategy, fail fast, integrity test (§8) |
| 7 | Expected messages hard-coded in steps | Many edits for one wording change | `messages_en.properties` (§9) |
| 8 | Implicit waits and `Thread.sleep` | Slow and flaky | Explicit waits only, in `Actions` (§11.1) |
| 9 | Assertions inside page objects | Pages can't be reused for negative scenarios | Assertions only in steps, with AssertJ descriptions (§12.1) |
| 10 | A "context" class with dozens of public fields | Grows forever, not thread-safe | `ScenarioContext` ThreadLocal map + key constants (§13.3) |
| 11 | Money compared as `double` | Rounding causes false failures | `BigDecimal` + `isEqualByComparingTo` (§11.4, §14.7) |
| 12 | Test data built through the UI for every scenario | Slow, flaky suites | API-based setup (§14.4) |
| 13 | Hardcoded tags in the runner | Manual edits for every run | `-Dcucumber.filter.tags` (§13.1, §22) |
| 14 | Imperative Gherkin ("When I click…") | Unreadable, brittle scenarios | Declarative business steps (§13.6) |
| 15 | Logs, reports or driver binaries committed | Repo bloat, stale drivers | Everything under `target/`; Selenium Manager for drivers (§4, §18, Appendix A) |
| 16 | Expectations changed to match the app's behaviour | Real defects hidden | `@knownIssue` tag + documented finding (§13.6) |

---

## 21. Coding Standards and Conventions

**Naming**
| Item | Convention | Example |
|---|---|---|
| Folders / packages | lower-case, by role (§4) | `pages`, `stepdefinitions`, `runner`, `utils` |
| Page classes | `<Name>Page` | `TransferFundsPage` |
| Components | `<Name>Component` | `AccountsTableComponent` |
| API clients | `<Resource>Api` | `TransferApi` |
| Step classes | `<Feature>Steps` / `<Feature>ApiSteps` | `TransferSteps`, `TransferApiSteps` |
| Feature files | kebab-case, one business capability | `transfer-funds.feature` |
| Locator files | kebab-case = page name | `transfer-funds.properties` |
| Locator keys | `<element>.<type>` | `amount.input`, `transfer.button`, `from.account.select` |
| Message keys | `<module>.<context>.<meaning>` | `billpay.validation.payee.required` |
| Constants | UPPER_SNAKE | `ScenarioContext.CHECKING_ACCOUNT_ID` |

**Rules**
1. No `Thread.sleep`, implicit waits, `System.out` or `printStackTrace`.
2. No `throws Exception` on every method. Use unchecked framework exceptions with clear messages.
3. No empty `catch` blocks. Log and rethrow, or handle meaningfully.
4. No commented-out code or scenarios. Git keeps the history.
5. Every assertion has a description (`.as("…")`).
6. Every scenario is **independent** and can run alone, in any order, in parallel.
7. One behaviour per scenario; steps stay thin and call pages or API clients.
8. Money is always `BigDecimal`.
9. Git flow: `feature/<short-desc>` → pull request → green smoke → merge.
10. Every new page needs its locator file, `isLoaded()`, and at least one `@smoke` scenario.

**Pull request checklist**
- [ ] No hardcoded locators, messages, URLs or credentials in Java.
- [ ] Thread-safety checklist (§15.2) passed.
- [ ] New scenarios are tagged (`@ui`/`@api`, `@smoke`/`@regression`).
- [ ] Runs green locally in parallel (`-DdpThreads=4`) on Chrome and Firefox.

---

## 22. How to Run

```bash
# Smoke against the public ParaBank site, Chrome, headed
mvn clean test -Dcucumber.filter.tags="@smoke"

# Everything except work in progress and known issues
mvn clean test -Dcucumber.filter.tags="not @wip and not @knownIssue"

# API scenarios only (no browser, fastest)
mvn clean test -Dcucumber.filter.tags="@api and not @ui"

# Against local Docker ParaBank, Firefox, headless, 4 parallel scenarios
docker run -d -p 8080:8080 parasoft/parabank
mvn clean test -Plocal -Dbrowser=firefox -Dheadless=true -DdpThreads=4

# One feature file
mvn clean test -Dcucumber.features="src/test/resources/features/login.feature"

# Re-run only the scenarios that failed last time
mvn test -Dcucumber.features="@target/rerun.txt"

# Report
mvn allure:serve
```

> If a scenario needs a secret, set it first, e.g. on Linux/macOS: `export PARABANK_PASSWORD=…`, or in PowerShell: `$env:PARABANK_PASSWORD="…"`.

---

## 23. Implementation Roadmap

| Phase | Duration (indicative) | Deliverables | Exit criteria |
|---|---|---|---|
| **0. Setup** | 1 day | Repo, `pom.xml`, `.gitignore`, README, folder layout (§4) | `mvn clean verify` green on Windows and Linux |
| **1. Core** | 3–4 days | `utils/`: Config, Driver, Locators, Messages, Actions, TestData, ScenarioContext, Money, FrameworkException, SuiteListener | LocatorIntegrityTest passes |
| **2. BDD + first UI feature** | 2–3 days | Runner, hooks; register + login pages, `login.feature` tagged with `@LOG-xx` IDs | Login scenarios green, screenshot attached on a forced failure |
| **3. API layer** | 2–3 days | RequestSpecFactory, MaskingLogFilter, clients, models, schemas, TestDataSetup | `@api` transfer scenarios green, no browser started |
| **4. Banking features** | 1 week | Open account, transfer, bill pay, find transactions, loan: UI, API and hybrid scenarios | 15–20 scenarios green in parallel (4 threads) on Chrome and Firefox |
| **5. Reporting and CI** | 2 days | AllureAttachments, GitHub Actions with ParaBank service container, Allure report published to GitHub Pages | Green CI badge, public report link and an Allure screenshot in README |
| **6. Hardening** | ongoing | Known-issue findings documented, dependency upgrades, optional Selenium Grid | Pass rate above 95% without re-runs |


### Portfolio showcase checklist
This repo doubles as a freelancing portfolio piece. A reviewer usually spends about two minutes on it, so S1–S7 must be true by the end of phase 5 (S8 grows during phase 6):

| # | What the reviewer sees | Delivered in | Where |
|---|---|---|---|
| S1 | README: what is tested, one-line run command | Phase 0 (kept current every phase) | `README.md` |
| S2 | README: screenshot of the Allure report | Phase 5 | `docs/images/allure-report.png`, linked from README |
| S3 | Green CI badge at the top of the README | Phase 5 | `.github/workflows/tests.yml` (§19) |
| S4 | Readable `.feature` files tagged with test-plan IDs (`@LOG-01`, `@TRF-02`) | Phase 2 onwards | `src/test/resources/features/` (§13.7) |
| S5 | A mix of UI, API and hybrid UI + API scenarios | Phases 2–4 | §14.6 scenario styles |
| S6 | Parallel runs (`-DdpThreads=4`) shown in README and CI | Phase 4 (README), phase 5 (CI) | §15 |
| S7 | Public Allure report link | Phase 5 | GitHub Pages (§19) |
| S8 | `@knownIssue` findings written up (real defects found in ParaBank) | Phase 6 | README "Findings" section |

---

### Appendix A: `.gitignore`
```gitignore
# Build / output
target/
allure-results/
allure-report/
test-output/
logs/
*.log

# IDE
.idea/
*.iml
.vscode/
.classpath
.project
.settings/

# OS
.DS_Store
Thumbs.db

# Local secrets (never commit)
.env
*.secret.properties
```

### Appendix B: Errors
There is one framework error type, `utils/FrameworkException` (unchecked, so no `throws Exception` everywhere). The message always says what is wrong and where: `Missing config key: browser (env=local)`, `Missing locator key: login.login.button`, `Secret/environment variable not set: PARABANK_PASSWORD`. Allure groups them by message text (`allure-categories.json`).

---
*End of design document. Keep it next to the code (`docs/FRAMEWORK_DESIGN.md`) and update it in the same pull request as any architectural change.*
