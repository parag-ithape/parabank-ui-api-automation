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
| G4 | **Parallel-safe** | `ThreadLocal` driver and context. No static mutable state. A new page object for every scenario. |
| G5 | **Fast and stable** | Test data is created through the API, not by clicking. Explicit waits only (no implicit wait, no `Thread.sleep`). Browser started only for `@ui` scenarios. |
| G6 | **Environment-agnostic** | One `baseUrl` switches between the public ParaBank site and a local/CI Docker instance. Values resolve: system property → environment variable → env file → defaults. |
| G7 | **Rich reporting** | Allure with Gherkin steps, screenshots, page source and API request/response attachments. |
| G8 | **Security** | No secrets in git. Credentials come from environment variables or GitHub Secrets and are masked in logs and reports. |
| G9 | **CI-ready** | Runs headless on Linux against a private ParaBank container and publishes the Allure report to GitHub Pages. |
| G10 | **Reusable core** | The framework core lives in `src/main/java`, so it can later be packaged as a jar and reused for another app. |

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
│                         EXECUTION LAYER                                      │
│  Maven → Surefire → TestNG suite → Cucumber TestNG runner                    │
│  Tags select what runs: @ui  @api  @smoke  @regression                       │
└───────────────┬──────────────────────────────────────────────────────────────┘
                │
┌───────────────▼──────────────────────────────────────────────────────────────┐
│               BDD LAYER  (src/test/resources/features + bdd/)                │
│  features/ui | api | hybrid/*.feature   (Gherkin, business language)         │
│  steps/ui | api | common   ── call pages or API clients, assert with AssertJ │
│  hooks/   ── browser only for @ui, screenshot on failure, context cleanup    │
└───────────────┬───────────────────────────────────┬──────────────────────────┘
                │ uses                              │ uses
┌───────────────▼─────────────────────┐ ┌──────────▼───────────────────────────┐
│   PAGE LAYER (pages/, components/)  │ │   API LAYER (api/clients, models)    │
│  LoginPage, AccountsOverviewPage,   │ │  AuthApi, CustomerApi, AccountApi,   │
│  TransferPage, BillPayPage …        │ │  TransferApi, LoanApi                │
│  locators/<page>.properties         │ │  TestDataSetup (API-built state)     │
└───────────────┬─────────────────────┘ └──────────┬───────────────────────────┘
                │ uses                              │ uses
┌───────────────▼───────────────────────────────────▼──────────────────────────┐
│                  FRAMEWORK CORE  (src/main/java)  ← reusable jar             │
│  config/   ConfigManager (sysprop + envvar + env file + defaults)            │
│  driver/   DriverFactory, DriverManager (ThreadLocal), BrowserOptionsFactory │
│  locators/ LocatorRepository (strategy:value → By)                           │
│  messages/ Messages (expected UI texts)                                      │
│  api/      RequestSpecFactory, MaskingLogFilter, ApiConfig                   │
│  data/     JsonDataReader, SecretResolver, FakeData                          │
│  actions/  ElementActions, WaitUtils, JsUtils                                │
│  context/  TestContext (ThreadLocal key/value), ContextKey                   │
│  listeners/ SuiteTuningListener, AllureEnvironmentListener                   │
│  reporting/ AllureAttachments                                                │
└───────────────┬──────────────────────────────────────────────────────────────┘
                │
┌───────────────▼──────────────────────────────────────────────────────────────┐
│  ParaBank: web UI (Chrome | Firefox | Edge) and REST API (/services/bank)    │
│  Public demo site  ──or──  local / CI Docker container (parasoft/parabank)   │
└──────────────────────────────────────────────────────────────────────────────┘
```

**Dependency rule (enforce in code review):** Steps → (Pages | API clients) → Core. The core never imports project code. Pages never import API clients or assertion libraries, and API clients never import pages.

---

## 4. Project Folder Structure

```
parabank-ui-api-automation/
├── pom.xml                                   # Build, dependencies, Surefire + Allure config
├── README.md                                 # What is tested, how to run, report link
├── .gitignore                                # target/, logs/, allure-results/, IDE files (NEVER pom.xml)
├── .editorconfig                             # Shared formatting rules
├── docs/
│   └── FRAMEWORK_DESIGN.md                   # This document
├── .github/workflows/tests.yml               # CI: ParaBank service container + Allure on Pages
│
├── src/main/java/com/parag/framework/        # ===== REUSABLE CORE (no ParaBank-specific code) =====
│   ├── config/
│   │   ├── ConfigManager.java                # Loads config.properties + env/<env>.properties; resolution order
│   │   └── ConfigKeys.java                   # Constants for every config key (no magic strings)
│   ├── driver/
│   │   ├── BrowserType.java                  # enum CHROME, FIREFOX, EDGE
│   │   ├── ExecutionMode.java                # enum LOCAL, GRID
│   │   ├── BrowserOptionsFactory.java        # Builds ChromeOptions/FirefoxOptions/EdgeOptions (headless, size…)
│   │   ├── DriverFactory.java                # Creates a WebDriver for (browser, mode)
│   │   └── DriverManager.java                # ThreadLocal<WebDriver> holder; get/set/quit
│   ├── locators/
│   │   ├── LocatorRepository.java            # One instance per page file; key → By
│   │   └── Locators.java                     # Cache + factory: Locators.forPage("login")
│   ├── messages/
│   │   └── Messages.java                     # Messages.get("transfer.complete.title")
│   ├── api/
│   │   ├── ApiConfig.java                    # API base path, timeouts
│   │   ├── RequestSpecFactory.java           # Shared REST Assured request spec
│   │   └── MaskingLogFilter.java             # Logs requests/responses with secrets masked
│   ├── data/
│   │   ├── JsonDataReader.java               # JSON → POJO / List<POJO>
│   │   ├── SecretResolver.java               # Resolves ${ENV_VAR} placeholders in data/config
│   │   └── FakeData.java                     # Datafaker wrapper: unique usernames, names, addresses…
│   ├── actions/
│   │   ├── WaitUtils.java                    # Explicit/fluent waits only
│   │   ├── ElementActions.java               # click/type/select with waits + retries + logs
│   │   └── JsUtils.java                      # JS click, scroll (last resort only)
│   ├── context/
│   │   ├── TestContext.java                  # ThreadLocal Map<String,Object> for sharing data within a scenario
│   │   └── ContextKey.java                   # Constants: CUSTOMER_ID, CHECKING_ACCOUNT_ID …
│   ├── listeners/
│   │   ├── SuiteTuningListener.java          # -DdpThreads overrides suite XML parallelism
│   │   └── AllureEnvironmentListener.java    # Writes environment.properties + categories.json
│   ├── reporting/
│   │   └── AllureAttachments.java            # screenshot, page source, browser console logs
│   ├── exceptions/
│   │   ├── FrameworkException.java           # Base unchecked exception
│   │   ├── ConfigException.java
│   │   ├── LocatorNotFoundException.java
│   │   └── TestDataException.java
│   └── utils/
│       ├── MoneyUtils.java                   # Parse "$1,234.50" → BigDecimal
│       └── PlaceholderUtils.java             # Safe {0},{1} replacement (XPath-safe, apostrophe-safe)
│
├── src/main/resources/
│   └── META-INF/services/org.testng.ITestNGListener   # Auto-registers core listeners (ServiceLoader)
│
├── src/test/java/com/parag/parabank/         # ===== PARABANK-SPECIFIC CODE =====
│   ├── pages/
│   │   ├── BasePage.java                     # Holds driver, actions, waits, locator repo; isLoaded()
│   │   ├── LoginPage.java
│   │   ├── RegisterPage.java
│   │   ├── AccountsOverviewPage.java
│   │   ├── OpenAccountPage.java
│   │   ├── TransferFundsPage.java
│   │   ├── BillPayPage.java
│   │   ├── FindTransactionsPage.java
│   │   └── RequestLoanPage.java
│   ├── components/
│   │   ├── BaseComponent.java
│   │   ├── LeftMenuComponent.java            # "Account Services" navigation
│   │   └── AccountsTableComponent.java       # Accounts overview table: row lookup, balances
│   ├── api/
│   │   ├── clients/
│   │   │   ├── AuthApi.java
│   │   │   ├── CustomerApi.java
│   │   │   ├── AccountApi.java
│   │   │   ├── TransferApi.java
│   │   │   └── LoanApi.java
│   │   ├── models/                           # Customer, Account, Transaction, LoanResponse (records)
│   │   └── data/
│   │       └── TestDataSetup.java            # "customer with two accounts and 500.00" via the API
│   ├── models/                               # UI test-data records: BankCustomer, Payee
│   └── bdd/
│       ├── runners/CucumberTestRunner.java
│       ├── hooks/CucumberHooks.java
│       └── steps/
│           ├── common/                       # Shared Given steps (data setup through the API)
│           ├── ui/                           # UI step definitions
│           └── api/                          # API step definitions
│
└── src/test/resources/
    ├── config/
    │   ├── config.properties                 # Defaults: browser, timeouts, headless…
    │   └── env/
    │       ├── public.properties             # baseUrl = public ParaBank site
    │       └── local.properties              # baseUrl = http://localhost:8080/parabank (Docker / CI)
    ├── locators/                             # ONE FILE PER PAGE/COMPONENT
    │   ├── common.properties                 # page title, error panel, generic table row
    │   ├── login.properties
    │   ├── register.properties
    │   ├── accounts-overview.properties
    │   ├── open-account.properties
    │   ├── transfer-funds.properties
    │   ├── bill-pay.properties
    │   ├── find-transactions.properties
    │   ├── request-loan.properties
    │   └── left-menu.properties
    ├── messages/
    │   └── messages_en.properties            # Expected UI messages (success, validation, errors)
    ├── testdata/
    │   ├── customers.json                    # Customer templates (no real passwords)
    │   └── payees.json                       # Bill-pay payees
    ├── schemas/                              # JSON schemas for API contract checks
    │   ├── account.json
    │   ├── customer.json
    │   └── transaction.json
    ├── features/
    │   ├── ui/                               # @ui scenarios
    │   ├── api/                              # @api scenarios
    │   └── hybrid/                           # @ui @api scenarios
    ├── suites/
    │   └── testng.xml                        # Runs the Cucumber runner + LocatorIntegrityTest
    ├── allure/
    │   └── categories.json                   # Failure classification for Allure
    ├── allure.properties                     # Allure results dir
    └── log4j2.xml                            # Console + rolling run log
```

**Why `src/main/java` for the core?** Classes in `src/test` can't be packaged and reused. With the core in `src/main`, you can later run `mvn install` and add it as a dependency to another project, or split it into a `framework-core` Maven module.

---

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

    <!-- Test engine. Compile scope because listeners in src/main implement TestNG interfaces. -->
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
            <suiteXmlFile>src/test/resources/suites/testng.xml</suiteXmlFile>
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

**`src/test/resources/config/config.properties`**: defaults shared by all environments
```properties
# ================= Browser / Execution =================
# chrome | firefox | edge
browser=chrome
# local | grid
execution.mode=local
headless=false
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

**`src/test/resources/config/env/public.properties`**: the shared public demo site
```properties
baseUrl=https://parabank.parasoft.com/parabank
```

**`src/test/resources/config/env/local.properties`**: Docker on a laptop or a service container in CI
```properties
baseUrl=http://localhost:8080/parabank
```

> `baseUrl` is shared by the UI and the API layer, so switching environment changes both at once.

### 6.2 Resolution order (highest priority wins)

```
1. JVM system property        -Dbrowser=firefox        (CI / command line)
2. Environment variable       BROWSER=firefox          (key upper-cased, '.' → '_')
3. env/<env>.properties       env selected by -Denv=local (default public)
4. config.properties          defaults
→ missing everywhere  ⇒  ConfigException("Missing config key: …")   (fail fast)
```

### 6.3 `ConfigManager.java`

```java
package com.parag.framework.config;

import com.parag.framework.exceptions.ConfigException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Central, read-only access point for all configuration.
 *
 * <p>Loads files from the CLASSPATH, not from file-system paths, so it works on Windows,
 * Linux CI runners and inside jars.</p>
 *
 * <p>Thread-safe: the Properties object is loaded once in a static initialiser and only read afterwards.</p>
 */
public final class ConfigManager {

    private static final String DEFAULT_FILE = "config/config.properties";
    private static final String ENV_FILE_PATTERN = "config/env/%s.properties";
    private static final Properties PROPS = load();

    private ConfigManager() { }

    /** Active environment name (public, local). Set with -Denv or the ENV variable. */
    public static String env() {
        String env = System.getProperty("env", System.getenv("ENV"));
        return (env == null || env.isBlank()) ? "public" : env.trim().toLowerCase();
    }

    private static Properties load() {
        Properties p = new Properties();
        loadInto(p, DEFAULT_FILE);                              // defaults first
        loadInto(p, String.format(ENV_FILE_PATTERN, env()));    // env file overrides defaults
        return p;
    }

    private static void loadInto(Properties target, String resource) {
        try (InputStream in = ConfigManager.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) throw new ConfigException("Config file not found on classpath: " + resource);
            target.load(in);
        } catch (IOException e) {
            throw new ConfigException("Unable to read config file: " + resource, e);
        }
    }

    /** Returns the resolved value or throws ConfigException if the key is missing everywhere. */
    public static String get(String key) {
        String sys = System.getProperty(key);
        if (sys != null && !sys.isBlank()) return sys.trim();

        String envVar = System.getenv(key.toUpperCase().replace('.', '_'));
        if (envVar != null && !envVar.isBlank()) return envVar.trim();

        String fileValue = PROPS.getProperty(key);
        if (fileValue == null) throw new ConfigException("Missing config key: " + key + " (env=" + env() + ")");
        return fileValue.trim();
    }

    public static String get(String key, String defaultValue) {
        try { return get(key); } catch (ConfigException e) { return defaultValue; }
    }

    public static int getInt(String key)         { return Integer.parseInt(get(key)); }
    public static boolean getBoolean(String key) { return Boolean.parseBoolean(get(key)); }
}
```

### 6.4 Secrets
- Passwords and tokens **never** go into git.
- Data files refer to them as `${PARABANK_PASSWORD}`, and `SecretResolver` (§10) replaces the placeholder from an environment variable or system property at runtime.
- Customers created by the suite get **generated** passwords (`FakeData`), so most scenarios need no secret at all.
- In CI, any secret comes from GitHub Secrets. Locally, export it in your shell or IDE run configuration (**not** a committed `.env` file).
- Mask secrets: override `toString()` on models that hold passwords, never put a password in an Allure `@Step` title, and log API calls through `MaskingLogFilter` (§14).

---

## 7. Driver Management

### 7.1 `BrowserType.java` / `ExecutionMode.java`
```java
package com.parag.framework.driver;

/** Supported browsers. from() gives a clear error for typos such as "chorme". */
public enum BrowserType {
    CHROME, FIREFOX, EDGE;

    public static BrowserType from(String value) {
        try {
            return BrowserType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("Unsupported browser: '" + value + "'. Use chrome|firefox|edge");
        }
    }
}

/** Where the browser runs: on this machine or on a Selenium Grid. */
public enum ExecutionMode {
    LOCAL, GRID;
    public static ExecutionMode from(String v) { return ExecutionMode.valueOf(v.trim().toUpperCase()); }
}
```

### 7.2 `BrowserOptionsFactory.java`
```java
package com.parag.framework.driver;

/**
 * Builds browser-specific options in ONE place. The same options are used locally and on a Grid,
 * so behaviour is identical everywhere.
 */
public final class BrowserOptionsFactory {

    private BrowserOptionsFactory() { }

    public static MutableCapabilities create(BrowserType browser) {
        boolean headless = ConfigManager.getBoolean("headless");
        String size = ConfigManager.get("window.width") + "," + ConfigManager.get("window.height");
        PageLoadStrategy pls = PageLoadStrategy.fromString(ConfigManager.get("page.load.strategy"));

        return switch (browser) {
            case CHROME -> {
                ChromeOptions o = new ChromeOptions();
                if (headless) o.addArguments("--headless=new");          // new headless = same engine as headed
                o.addArguments("--window-size=" + size, "--disable-notifications");
                o.setPageLoadStrategy(pls);
                yield o;
            }
            case EDGE -> {
                EdgeOptions o = new EdgeOptions();                        // Chromium based → same flags as Chrome
                if (headless) o.addArguments("--headless=new");
                o.addArguments("--window-size=" + size);
                o.setPageLoadStrategy(pls);
                yield o;
            }
            case FIREFOX -> {
                FirefoxOptions o = new FirefoxOptions();
                if (headless) o.addArguments("-headless");
                o.addArguments("--width=" + ConfigManager.get("window.width"),
                               "--height=" + ConfigManager.get("window.height"));
                o.setPageLoadStrategy(pls);
                yield o;
            }
        };
    }
}
```

### 7.3 `DriverFactory.java`
```java
package com.parag.framework.driver;

/**
 * Creates fully configured WebDriver instances. This is the ONLY class that calls "new XxxDriver".
 * Selenium Manager (built into Selenium 4.6+) resolves driver binaries automatically,
 * so no driver .exe files are committed to the repo.
 */
public final class DriverFactory {

    private DriverFactory() { }

    public static WebDriver create(BrowserType browser) {
        ExecutionMode mode = ExecutionMode.from(ConfigManager.get("execution.mode"));
        MutableCapabilities options = BrowserOptionsFactory.create(browser);

        WebDriver driver = switch (mode) {
            case LOCAL -> createLocal(browser, options);
            case GRID  -> createRemote(ConfigManager.get("grid.url"), options);
        };

        // Timeouts. Note: NO implicit wait (explicit waits only, see WaitUtils).
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(ConfigManager.getInt("timeout.pageload")));
        driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(ConfigManager.getInt("timeout.script")));
        return driver;
    }

    private static WebDriver createLocal(BrowserType browser, Capabilities options) {
        return switch (browser) {
            case CHROME  -> new ChromeDriver((ChromeOptions) options);
            case FIREFOX -> new FirefoxDriver((FirefoxOptions) options);
            case EDGE    -> new EdgeDriver((EdgeOptions) options);
        };
    }

    private static WebDriver createRemote(String url, Capabilities options) {
        try {
            return new RemoteWebDriver(URI.create(url).toURL(), options);
        } catch (MalformedURLException e) {
            throw new FrameworkException("Invalid remote WebDriver URL: " + url, e);
        }
    }
}
```

### 7.4 `DriverManager.java`: the heart of parallel safety
```java
package com.parag.framework.driver;

/**
 * Holds one WebDriver PER THREAD.
 *
 * <p>Why ThreadLocal: scenarios run in parallel on a thread pool. A static WebDriver field would be
 * shared by all threads, so scenario A would click in scenario B's browser. ThreadLocal gives every
 * thread its own browser.</p>
 *
 * <p>Always call quit() in the @After hook. It also calls remove() so pooled threads don't keep a
 * dead driver (memory leak / "session not found" errors).</p>
 */
public final class DriverManager {

    private static final ThreadLocal<WebDriver> DRIVER = new ThreadLocal<>();

    private DriverManager() { }

    public static WebDriver getDriver() {
        WebDriver driver = DRIVER.get();
        if (driver == null) {
            throw new IllegalStateException("WebDriver not initialised for thread " + Thread.currentThread().getName()
                    + ". Is the scenario tagged @ui? Is the page created inside a step?");
        }
        return driver;
    }

    public static void setDriver(WebDriver driver) { DRIVER.set(driver); }

    public static boolean hasDriver() { return DRIVER.get() != null; }

    public static void quit() {
        WebDriver driver = DRIVER.get();
        if (driver != null) {
            try { driver.quit(); } finally { DRIVER.remove(); }
        }
    }
}
```

---

## 8. Locator Repository

Locators live in **separate files, one per page or component**, not in Java code. A UI change is then a one-line edit in a `.properties` file, and every locator is validated before the suite runs.

| Feature | Design |
|---|---|
| File layout | **1 file per page/component** (`locators/<page>.properties`) |
| Strategy | `id`, `name`, `css`, `xpath`, `linkText`, `partialLinkText`, `className`, `tagName` |
| Missing key | `LocatorNotFoundException("login.username.input")` **at page construction** |
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

### 8.2 `LocatorRepository.java` / `Locators.java`
```java
package com.parag.framework.locators;

/** Holds the locators of ONE page file and converts "strategy:value" strings to Selenium By objects. */
public final class LocatorRepository {

    private final String pageName;
    private final Properties entries;

    LocatorRepository(String pageName) {
        this.pageName = pageName;
        this.entries = new Properties();
        String resource = "locators/" + pageName + ".properties";
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (in == null) throw new LocatorNotFoundException("Locator file not found: " + resource);
            entries.load(in);
        } catch (IOException e) {
            throw new LocatorNotFoundException("Cannot read locator file: " + resource, e);
        }
    }

    /**
     * @param key  key inside the page file, e.g. "login.button"
     * @param args values for {0},{1}… placeholders (for dynamic locators)
     */
    public By by(String key, Object... args) {
        String raw = entries.getProperty(key);
        if (raw == null) throw new LocatorNotFoundException(pageName + "." + key);
        // PlaceholderUtils, NOT MessageFormat: MessageFormat treats single quotes as escape characters
        // and would silently break XPaths such as //td[text()='{0}'].
        String resolved = PlaceholderUtils.fill(raw, args);
        return toBy(key, resolved);
    }

    private By toBy(String key, String raw) {
        int sep = raw.indexOf(':');                       // split on the FIRST ':' only (XPath may contain ':')
        if (sep < 1) throw new LocatorNotFoundException("Bad locator format for " + pageName + "." + key + " → " + raw);
        String strategy = raw.substring(0, sep).trim();
        String value = raw.substring(sep + 1).trim();
        return switch (strategy.toLowerCase()) {
            case "id"              -> By.id(value);
            case "name"            -> By.name(value);
            case "css"             -> By.cssSelector(value);
            case "xpath"           -> By.xpath(value);
            case "linktext"        -> By.linkText(value);
            case "partiallinktext" -> By.partialLinkText(value);
            case "classname"       -> By.className(value);
            case "tagname"         -> By.tagName(value);
            default -> throw new LocatorNotFoundException("Unknown strategy '" + strategy + "' in " + pageName + "." + key);
        };
    }

    /** Used by LocatorIntegrityTest to validate every entry without a browser. */
    public Set<String> keys() { return entries.stringPropertyNames(); }
}
```
```java
package com.parag.framework.locators;

/** Cache of LocatorRepository objects. Each file is read once and then shared read-only by all threads. */
public final class Locators {
    private static final Map<String, LocatorRepository> CACHE = new ConcurrentHashMap<>();
    private Locators() { }

    public static LocatorRepository forPage(String pageName) {
        return CACHE.computeIfAbsent(pageName, LocatorRepository::new);
    }
}
```
```java
package com.parag.framework.utils;

/** Replaces {0},{1}… literally. Safe for XPath quotes and for apostrophes in messages. */
public final class PlaceholderUtils {
    private PlaceholderUtils() { }
    public static String fill(String template, Object... args) {
        String result = template;
        for (int i = 0; args != null && i < args.length; i++) {
            result = result.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return result;
    }
}
```

### 8.3 Locator integrity test (catches broken locators before the scenarios run)
```java
// src/test/java/com/parag/parabank/framework/LocatorIntegrityTest.java
// Parses EVERY key in EVERY locators/*.properties file. No browser needed, runs in under a second.
// Listed first in suites/testng.xml, so a typo fails the build before any browser starts.
public class LocatorIntegrityTest {
    @Test
    public void allLocatorFilesAreValid() throws Exception {
        Path dir = Path.of(getClass().getClassLoader().getResource("locators").toURI());
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(f -> f.toString().endsWith(".properties")).forEach(f -> {
                String page = f.getFileName().toString().replace(".properties", "");
                LocatorRepository repo = Locators.forPage(page);
                repo.keys().forEach(k -> repo.by(k, "x", "y", "z"));   // throws on bad strategy/format
            });
        }
    }
}
```

> **Why not `@FindBy` / PageFactory?** PageFactory can't read locators from external files. Its lazy proxies also cause `StaleElementReferenceException` when the page re-renders, and the Selenium team no longer recommends it. Using `By` plus waits on every action is more robust.

---

## 9. Message Repository

Expected UI texts (confirmation titles, validation errors) live in **`messages/messages_en.properties`**. Steps compare UI text against these keys, so a wording change in the app is a **one-line fix**.

> Confirm each text against the live app while building; the values below show the format.

**`src/test/resources/messages/messages_en.properties`**
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

```java
package com.parag.framework.messages;

/**
 * Expected UI messages. The locale comes from config (messages.locale).
 * Files are read as UTF-8 so accented characters work.
 */
public final class Messages {

    private static final Properties MESSAGES = load(ConfigManager.get("messages.locale", "en"));

    private Messages() { }

    private static Properties load(String locale) {
        String resource = "messages/messages_" + locale + ".properties";
        try (InputStream in = Messages.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) throw new FrameworkException("Messages file not found: " + resource);
            Properties p = new Properties();
            p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            return p;
        } catch (IOException e) {
            throw new FrameworkException("Cannot load messages: " + resource, e);
        }
    }

    /** Messages.get("register.success.title", "bdd_user_1731") */
    public static String get(String key, Object... args) {
        String msg = MESSAGES.getProperty(key);
        if (msg == null) throw new FrameworkException("Missing message key: " + key);
        return PlaceholderUtils.fill(msg, args);
    }
}
```

---

## 10. Test Data Management

### 10.1 Strategy

| Data type | Format | Location | Example |
|---|---|---|---|
| Customer templates | JSON | `testdata/customers.json` | address, phone, SSN pattern |
| Bill-pay payees | JSON | `testdata/payees.json` | name, address, account number |
| Unique values (username, names) | Generated | `FakeData` | `FakeData.username()` → `bdd_1731..._7` |
| Starting state (accounts, balances) | API | `api/data/TestDataSetup` | customer with checking 500.00 + empty savings |
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
package com.parag.parabank.models;

/** Immutable customer record. toString() masks the password, so it never appears in Allure parameters or logs. */
public record BankCustomer(String firstName, String lastName, String street, String city, String state,
                           String zipCode, String phone, String ssn, String username, String password) {
    @Override public String toString() {
        return "BankCustomer[username=" + username + ", name=" + firstName + " " + lastName + "]";
    }
}
```

### 10.4 `JsonDataReader.java` + `SecretResolver.java`
```java
package com.parag.framework.data;

/** Reads JSON from the classpath and maps it to POJOs/records. ${VAR} placeholders are resolved first. */
public final class JsonDataReader {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private JsonDataReader() { }

    public static <T> List<T> readList(String resource, Class<T> type) {
        try {
            String json = SecretResolver.resolve(readResource(resource));
            return MAPPER.readValue(json, MAPPER.getTypeFactory().constructCollectionType(List.class, type));
        } catch (IOException e) {
            throw new TestDataException("Cannot parse test data: " + resource, e);
        }
    }

    public static <T> T read(String resource, Class<T> type) { /* same idea, single object */ }

    private static String readResource(String resource) throws IOException {
        try (InputStream in = JsonDataReader.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) throw new TestDataException("Test data not found: " + resource);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
```
```java
package com.parag.framework.data;

/** Replaces ${NAME} with System.getProperty(NAME) or System.getenv(NAME). Fails fast if neither is set. */
public final class SecretResolver {
    private static final Pattern P = Pattern.compile("\\$\\{([A-Z0-9_]+)}");
    private SecretResolver() { }

    public static String resolve(String text) {
        Matcher m = P.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String name = m.group(1);
            String value = Optional.ofNullable(System.getProperty(name)).orElse(System.getenv(name));
            if (value == null) throw new TestDataException("Secret/env variable not set: " + name);
            m.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
```

### 10.5 `FakeData.java`
```java
package com.parag.framework.data;

/** Unique, parallel-safe data. Time + thread id + a JVM-wide counter: no collision even when one thread generates twice in the same millisecond. */
public final class FakeData {
    private static final Faker FAKER = new Faker();
    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private FakeData() { }

    public static String username() {
        return "bdd_" + System.currentTimeMillis() + "_" + Thread.currentThread().getId() + "_" + SEQUENCE.incrementAndGet();
    }
    public static String password()  { return "Pw!" + FAKER.internet().password(10, 14, true, false, true); }
    public static String firstName() { return FAKER.name().firstName(); }
    public static String lastName()  { return FAKER.name().lastName(); }
}
```

---

## 11. Core Utilities

### 11.1 `WaitUtils.java`
```java
package com.parag.framework.actions;

/**
 * ALL synchronisation goes through this class. Rules:
 *  - No implicit waits anywhere (Selenium docs: mixing them gives unpredictable wait times).
 *  - No Thread.sleep(): slow AND flaky.
 *  - Polls every 250 ms and ignores stale elements while waiting.
 */
public class WaitUtils {

    private final WebDriverWait wait;

    public WaitUtils(WebDriver driver) {
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(ConfigManager.getInt("timeout.explicit")));
        this.wait.pollingEvery(Duration.ofMillis(ConfigManager.getInt("timeout.polling.millis")))
                 .ignoring(StaleElementReferenceException.class);
    }

    public WebElement visible(By by)      { return wait.until(ExpectedConditions.visibilityOfElementLocated(by)); }
    public WebElement clickable(By by)    { return wait.until(ExpectedConditions.elementToBeClickable(by)); }
    public WebElement present(By by)      { return wait.until(ExpectedConditions.presenceOfElementLocated(by)); }
    public List<WebElement> allVisible(By by) { return wait.until(ExpectedConditions.visibilityOfAllElementsLocatedBy(by)); }
    public boolean invisible(By by)       { return wait.until(ExpectedConditions.invisibilityOfElementLocated(by)); }
    public boolean textPresent(By by, String text) { return wait.until(ExpectedConditions.textToBePresentInElementLocated(by, text)); }
    public boolean urlContains(String part) { return wait.until(ExpectedConditions.urlContains(part)); }

    /** ParaBank fills some dropdowns (account lists) after the page loads: wait until options exist. */
    public void optionsLoaded(By select) {
        until(d -> new Select(d.findElement(select)).getOptions().size() > 0, "Options loaded in " + select);
    }

    /** Waits for document.readyState == complete. */
    public void pageReady() {
        wait.until(d -> "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState")));
    }

    /** Custom condition with a readable timeout message. */
    public <T> T until(Function<WebDriver, T> condition, String description) {
        return wait.withMessage(description).until(condition);
    }
}
```

### 11.2 `ElementActions.java`
```java
package com.parag.framework.actions;

/**
 * Every interaction with the browser goes through here, so waits, retries and logging live in ONE place.
 * Pages call these methods and never call driver.findElement(...).click() directly.
 */
public class ElementActions {

    private static final Logger LOG = LogManager.getLogger(ElementActions.class);
    private final WebDriver driver;
    private final WaitUtils wait;
    private final int clickRetries = ConfigManager.getInt("click.retry.count");

    public ElementActions(WebDriver driver, WaitUtils wait) { this.driver = driver; this.wait = wait; }

    /** Click with a retry on stale/intercepted elements (page re-renders, overlays). */
    public void click(By by) {
        for (int attempt = 1; ; attempt++) {
            try {
                wait.clickable(by).click();
                LOG.info("Clicked {}", by);
                return;
            } catch (StaleElementReferenceException | ElementClickInterceptedException e) {
                if (attempt >= clickRetries) throw e;
                LOG.warn("Retry {}/{} click on {} due to {}", attempt, clickRetries, by, e.getClass().getSimpleName());
            }
        }
    }

    /** Clears, then types. Use typeSecret() for passwords so the value is never logged. */
    public void type(By by, String text) {
        WebElement el = wait.visible(by);
        el.clear();
        el.sendKeys(text);
        LOG.info("Typed '{}' into {}", text, by);
    }

    public void typeSecret(By by, String secret) {
        WebElement el = wait.visible(by);
        el.clear();
        el.sendKeys(secret);
        LOG.info("Typed ****** into {}", by);
    }

    public String text(By by)                { return wait.visible(by).getText().trim(); }
    public String attribute(By by, String a) { return wait.present(by).getAttribute(a); }

    /** Non-throwing visibility check: returns false instead of throwing when the element is absent. */
    public boolean isDisplayed(By by) {
        try { return !driver.findElements(by).isEmpty() && driver.findElement(by).isDisplayed(); }
        catch (StaleElementReferenceException e) { return false; }
    }

    public void selectByText(By by, String visibleText)  { new Select(wait.visible(by)).selectByVisibleText(visibleText); }
    public void selectByValue(By by, String value)        { new Select(wait.visible(by)).selectByValue(value); }
    public List<String> optionTexts(By by) {
        return new Select(wait.visible(by)).getOptions().stream().map(WebElement::getText).toList();
    }
}
```

### 11.3 `JsUtils.java`
```java
/** Use JavaScript ONLY as a last resort (it bypasses real user behaviour). Document why at each call site. */
public class JsUtils {
    private final JavascriptExecutor js;
    public JsUtils(WebDriver driver) { this.js = (JavascriptExecutor) driver; }
    public void click(WebElement el)          { js.executeScript("arguments[0].click();", el); }
    public void scrollIntoView(WebElement el) { js.executeScript("arguments[0].scrollIntoView({block:'center'});", el); }
}
```

### 11.4 `MoneyUtils.java`
```java
/** Bank amounts are always BigDecimal: never double, which loses cents to rounding. */
public final class MoneyUtils {
    private MoneyUtils() { }

    /** "$1,234.50" or "-$20.00" → BigDecimal */
    public static BigDecimal parse(String uiText) {
        String cleaned = uiText.replace("$", "").replace(",", "").trim();
        return new BigDecimal(cleaned);
    }
}
```

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
package com.parag.parabank.pages;

/**
 * Parent of all pages. It gives every page:
 *  - the current thread's driver (from DriverManager, never a copied static field)
 *  - waits / actions / js helpers
 *  - its own LocatorRepository (file name = pageName)
 *  - the shared "common" locators
 */
public abstract class BasePage {

    protected final WebDriver driver;
    protected final WaitUtils wait;
    protected final ElementActions actions;
    protected final JsUtils js;
    protected final LocatorRepository loc;
    protected final LocatorRepository common = Locators.forPage("common");
    protected final Logger log = LogManager.getLogger(getClass());

    protected BasePage(String pageName) {
        this.driver = DriverManager.getDriver();
        this.wait = new WaitUtils(driver);
        this.actions = new ElementActions(driver, wait);
        this.js = new JsUtils(driver);
        this.loc = Locators.forPage(pageName);
    }

    /** Each page defines what "loaded" means (a unique element). */
    public abstract boolean isLoaded();

    /** Generic page-load check that fails with a clear message. */
    @SuppressWarnings("unchecked")
    protected <T extends BasePage> T waitUntilLoaded() {
        wait.until(d -> isLoaded(), getClass().getSimpleName() + " did not load");
        return (T) this;
    }

    public String getPageTitle() { return actions.text(common.by("page.title")); }
}
```

### 12.3 `LoginPage.java`
```java
package com.parag.parabank.pages;

public class LoginPage extends BasePage {

    // Resolved at construction, so a missing locator key fails immediately with the page + key name
    private final By username = loc.by("username.input");
    private final By password = loc.by("password.input");
    private final By loginBtn = loc.by("login.button");
    private final By error    = loc.by("error.message");

    public LoginPage() { super("login"); }

    @Step("Open ParaBank")
    public LoginPage open() {
        driver.get(ConfigManager.get("baseUrl") + "/index.htm");
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
package com.parag.parabank.pages;

public class TransferFundsPage extends BasePage {

    private final By amount      = loc.by("amount.input");
    private final By fromAccount = loc.by("from.account.select");
    private final By toAccount   = loc.by("to.account.select");
    private final By transferBtn = loc.by("transfer.button");
    private final By resultTitle = loc.by("result.title");

    public TransferFundsPage() { super("transfer-funds"); }

    @Step("Transfer {value} from account {fromId} to account {toId}")
    public TransferFundsPage transfer(BigDecimal value, long fromId, long toId) {
        wait.optionsLoaded(fromAccount);                 // account lists are filled after page load
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
package com.parag.parabank.components;

/** The accounts overview table, written once and used by every page that lists accounts. */
public class AccountsTableComponent extends BaseComponent {

    public AccountsTableComponent() { super("accounts-overview"); }

    /** Balance shown in the UI for one account, as BigDecimal (never double). */
    public BigDecimal balanceOf(long accountId) {
        String text = actions.text(loc.by("balance.byAccountId", accountId));   // dynamic locator {0}
        return MoneyUtils.parse(text);
    }

    public List<Long> accountIds() { /* reads the account-number links in the table */ }
}
```

---

## 13. BDD Layer (Cucumber on TestNG)

Cucumber is the **primary way tests are written**. Pages, API clients, locators, messages, data, drivers and Allure all sit underneath it.

```
src/test/java/com/parag/parabank/bdd/
├── runners/CucumberTestRunner.java
├── hooks/CucumberHooks.java
└── steps/common | ui | api
src/test/resources/features/ui | api | hybrid/*.feature
```

### 13.1 Runner (parallel scenarios)
```java
@CucumberOptions(
    features = "src/test/resources/features",
    glue = {"com.parag.parabank.bdd.steps", "com.parag.parabank.bdd.hooks"},
    plugin = {"pretty",
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

**`src/test/resources/suites/testng.xml`**
```xml
<!DOCTYPE suite SYSTEM "https://testng.org/testng-1.0.dtd">
<!-- data-provider-thread-count = how many scenarios run at once. Override with -DdpThreads=N. -->
<suite name="ParaBank" data-provider-thread-count="4">
  <test name="Framework checks" preserve-order="true">
    <classes><class name="com.parag.parabank.framework.LocatorIntegrityTest"/></classes>
  </test>
  <test name="Scenarios">
    <classes><class name="com.parag.parabank.bdd.runners.CucumberTestRunner"/></classes>
  </test>
</suite>
```

### 13.2 Hooks: browser only for `@ui`, cleanup for everything
```java
public class CucumberHooks {

    /** API-only scenarios never open a browser, so they stay fast. */
    @Before(value = "@ui", order = 0)
    public void startBrowser(Scenario scenario) {
        ThreadContext.put("scenario", scenario.getName().replaceAll("\\W+", "_"));
        String browser = ConfigManager.get("browser");
        ThreadContext.put("browser", browser);
        DriverManager.setDriver(DriverFactory.create(BrowserType.from(browser)));
    }

    /** Attach evidence BEFORE quitting: once the driver is gone, no screenshot can be taken. */
    @After(value = "@ui", order = 0)
    public void stopBrowser(Scenario scenario) {
        try {
            if (scenario.isFailed() && DriverManager.hasDriver()) {
                byte[] png = ((TakesScreenshot) DriverManager.getDriver()).getScreenshotAs(OutputType.BYTES);
                scenario.attach(png, "image/png", "Failure screenshot");
                if (ConfigManager.getBoolean("attach.page.source.on.failure")) AllureAttachments.pageSource();
                AllureAttachments.browserConsoleLogs();
            }
        } finally {
            DriverManager.quit();                  // always, even if attaching failed
        }
    }

    /** Runs for every scenario, UI or API: no data leaks into the next scenario on this thread. */
    @After(order = 1)
    public void clearContext() {
        TestContext.clear();
        ThreadContext.clearAll();
    }
}
```

### 13.3 `TestContext.java` and `ContextKey.java`
```java
package com.parag.framework.context;

/**
 * Per-thread key/value store for passing data between steps of ONE scenario,
 * e.g. the account IDs created through the API in a Given step and checked in a Then step.
 * Use ContextKey constants, not free-text strings.
 */
public final class TestContext {
    private static final ThreadLocal<Map<String, Object>> CTX = ThreadLocal.withInitial(HashMap::new);
    private TestContext() { }

    public static void put(String key, Object value) { CTX.get().put(key, value); }

    @SuppressWarnings("unchecked")
    public static <T> T get(String key) {
        Object v = CTX.get().get(key);
        if (v == null) throw new IllegalStateException("TestContext has no value for '" + key + "'");
        return (T) v;
    }

    public static void clear() { CTX.remove(); }
}

public final class ContextKey {
    public static final String CUSTOMER            = "customer";
    public static final String CUSTOMER_ID         = "customer.id";
    public static final String CHECKING_ACCOUNT_ID = "account.checking.id";
    public static final String SAVINGS_ACCOUNT_ID  = "account.savings.id";
    public static final String LAST_RESPONSE       = "api.last.response";
    private ContextKey() { }
}
```

### 13.4 Steps stay thin: they call pages or API clients, then assert
```java
public class TransferUiSteps {
    private TransferFundsPage transferPage;   // PicoContainer creates new step instances per scenario, so instance fields are safe

    @When("they transfer {bigdecimal} from checking to savings in the browser")
    public void transferInBrowser(BigDecimal amount) {
        transferPage = new LeftMenuComponent().openTransferFunds()
                .transfer(amount,
                          TestContext.get(ContextKey.CHECKING_ACCOUNT_ID),
                          TestContext.get(ContextKey.SAVINGS_ACCOUNT_ID));
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

**`features/ui/login.feature`**
```gherkin
@ui
Feature: Customer login

  @smoke
  Scenario: Registered customer can log in
    Given a newly registered customer
    When they log in with their username and password
    Then the accounts overview is displayed

  @regression
  Scenario Outline: Login is refused with invalid credentials
    When a visitor logs in with username "<username>" and password "<password>"
    Then the login error "<message>" is shown

    Examples:
      | username      | password | message            |
      | unknown_user  | wrong    | login.error.invalid |
      |               |          | login.error.empty   |
```

**`features/ui/bill-pay.feature`**
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
package com.parag.framework.api;

/** One shared request spec. ParaBank returns XML by default, so JSON is requested explicitly. */
public final class RequestSpecFactory {
    private RequestSpecFactory() { }

    public static RequestSpecification base() {
        return new RequestSpecBuilder()
                .setBaseUri(ConfigManager.get("baseUrl"))
                .setBasePath(ConfigManager.get("api.base.path"))
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
package com.parag.parabank.api.clients;

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
package com.parag.parabank.api.data;

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
        TestContext.put(ContextKey.CUSTOMER, customer);
        TestContext.put(ContextKey.CUSTOMER_ID, customerId);
        TestContext.put(ContextKey.CHECKING_ACCOUNT_ID, checkingId);
    }

    public void addSavingsAccount() { /* createAccount type 1, store SAVINGS_ACCOUNT_ID */ }

    private BankCustomer customerForThisThread() { /* register once per thread, then reuse */ }
}
```

### 14.5 `MaskingLogFilter.java`
```java
package com.parag.framework.api;

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
        .time(lessThan((long) ConfigManager.getInt("api.max.response.millis")));
Account account = response.as(Account.class);
assertThat(account.balance()).as("Checking balance after transfer").isEqualByComparingTo("300.00");
```

Money is compared with `BigDecimal.isEqualByComparingTo`, never with `double`, so `300.0` and `300.00` match and rounding never causes false failures.

---

## 15. Parallel Execution Strategy

### 15.1 How scenarios run in parallel
- `CucumberTestRunner.scenarios()` is a `@DataProvider(parallel = true)`, so **each scenario is one parallel invocation**.
- Pool size = `data-provider-thread-count` in `testng.xml`, overridable with `-DdpThreads=N` (`SuiteTuningListener`).
- Each scenario gets its own browser (`@ui` hook), its own `TestContext` and new step-class instances (PicoContainer).

```java
/** Overrides suite XML parallelism from the command line: mvn test -DdpThreads=6 */
public class SuiteTuningListener implements IAlterSuiteListener {
    @Override
    public void alter(List<XmlSuite> suites) {
        for (XmlSuite suite : suites) {
            Optional.ofNullable(System.getProperty("dpThreads")).map(Integer::parseInt)
                    .ifPresent(suite::setDataProviderThreadCount);
        }
    }
}
```

### 15.2 Thread-safety checklist (mandatory in code review)
- [ ] No `static` WebDriver, WebElement, page or test-data fields (`static final` constants are fine).
- [ ] The driver is only obtained through `DriverManager.getDriver()`.
- [ ] Page objects are created **inside** steps, never shared between scenarios.
- [ ] Shared runtime data goes in `TestContext` (ThreadLocal), never in static maps.
- [ ] Every scenario uses **its own accounts**; no two scenarios change the same account.
- [ ] Registered customers are cached **per thread** (`ThreadLocal`), never shared across threads.

### 15.3 Thread-count sizing
- Laptop: roughly **CPU cores − 1** browsers (each Chrome uses about 300–500 MB of RAM).
- Public ParaBank site: keep it **low (2–3)**. It's a shared demo; don't load it.
- Local or CI container: 4 is a good default. Increase step by step. If failures rise with thread count, look for shared state or environment capacity limits, not "flaky tests".

---

## 16. Cross-Browser Strategy

- The browser comes from `-Dbrowser=chrome|firefox|edge` (default `chrome`).
- **CI runs a browser matrix**: one job per browser (§19), so each browser has its own report and failures are easy to separate.
- **Coding rules:**
  - Browser-specific flags live only in `BrowserOptionsFactory`, never in pages.
  - Avoid CDP-only features (network interception) in scenarios that must run on Firefox.
  - Prefer `id`/`name`/CSS locators; use XPath only when the DOM gives nothing better.
- **Selenium Grid (optional, later):** `execution.mode=grid` with `grid.url` already works through `DriverFactory.createRemote`. A `docker-compose` Grid file can be added when the suite needs more parallel browsers than one machine can run.

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
| `CucumberHooks` on failure | Screenshot, page source, browser console log |
| Feature folders + tags | Behaviours tab grouped by feature; filter by `@smoke`, `@api`… |

### 17.3 `AllureAttachments.java`
```java
package com.parag.framework.reporting;

/** Every attachment helper in one place. All methods are safe to call even if the driver is broken. */
public final class AllureAttachments {
    private AllureAttachments() { }

    public static void pageSource() {
        try { Allure.addAttachment("Page source", "text/html", DriverManager.getDriver().getPageSource(), "html"); }
        catch (Exception e) { LogManager.getLogger().warn("Page source failed: {}", e.getMessage()); }
    }

    /** Browser console errors (Chromium browsers only; skipped silently elsewhere). */
    public static void browserConsoleLogs() {
        try {
            String logs = DriverManager.getDriver().manage().logs().get(LogType.BROWSER).getAll()
                    .stream().map(Object::toString).collect(Collectors.joining("\n"));
            if (!logs.isBlank()) Allure.addAttachment("Browser console", "text/plain", logs);
        } catch (Exception ignored) { /* not supported by this browser */ }
    }
}
```

### 17.4 Environment widget + failure categories
```java
/** Writes environment.properties (shown on the Allure overview) and copies categories.json before the run starts. */
public class AllureEnvironmentListener implements ISuiteListener {
    @Override
    public void onStart(ISuite suite) {
        Path results = Path.of(System.getProperty("allure.results.directory", "target/allure-results"));
        try {
            Files.createDirectories(results);
            Properties env = new Properties();
            env.setProperty("Environment", ConfigManager.env());
            env.setProperty("Base URL", ConfigManager.get("baseUrl"));
            env.setProperty("Browser", ConfigManager.get("browser"));
            env.setProperty("Java", System.getProperty("java.version"));
            env.setProperty("OS", System.getProperty("os.name"));
            try (OutputStream out = Files.newOutputStream(results.resolve("environment.properties"))) { env.store(out, null); }

            try (InputStream in = getClass().getClassLoader().getResourceAsStream("allure/categories.json")) {
                if (in != null) Files.copy(in, results.resolve("categories.json"), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LogManager.getLogger().warn("Could not write Allure environment: {}", e.getMessage());
        }
    }
}
```

**`src/test/resources/allure/categories.json`**: groups failures by root cause on the Allure "Categories" tab
```json
[
  { "name": "Product defects (assertion failed)", "matchedStatuses": ["failed"], "messageRegex": ".*(expected|Expecting).*" },
  { "name": "Locator / element issues",           "matchedStatuses": ["broken"], "traceRegex": ".*(NoSuchElementException|LocatorNotFoundException|StaleElementReferenceException).*" },
  { "name": "Timeouts / environment slowness",    "matchedStatuses": ["broken"], "traceRegex": ".*TimeoutException.*" },
  { "name": "API / network",                      "matchedStatuses": ["broken"], "traceRegex": ".*(ConnectException|SocketTimeoutException|HttpHostConnectException).*" },
  { "name": "Test data / config problems",        "matchedStatuses": ["broken"], "traceRegex": ".*(TestDataException|ConfigException).*" },
  { "name": "Skipped",                            "matchedStatuses": ["skipped"] }
]
```

**`src/main/resources/META-INF/services/org.testng.ITestNGListener`** (auto-registered, no suite XML entries needed)
```
com.parag.framework.listeners.SuiteTuningListener
com.parag.framework.listeners.AllureEnvironmentListener
```

### 17.5 Generating the report
```bash
mvn allure:serve      # local: builds the report and opens it in a browser
mvn allure:report     # CI: writes static HTML to target/site/allure-maven-plugin
```

---

## 18. Logging

**`src/test/resources/log4j2.xml`**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<!--
  Two outputs:
   1. Console               : what you see in the IDE and CI log
   2. target/logs/run.log   : the whole run, rolled at 20 MB (under target/, so never committed)
  %X{scenario} and %X{browser} come from ThreadContext, set in CucumberHooks, so parallel lines stay readable.
-->
<Configuration status="WARN">
  <Properties>
    <Property name="pattern">%d{HH:mm:ss.SSS} [%t] [%X{scenario}] [%X{browser}] %-5level %c{1} - %msg%n</Property>
    <Property name="logDir">target/logs</Property>
  </Properties>

  <Appenders>
    <Console name="Console" target="SYSTEM_OUT">
      <PatternLayout pattern="${pattern}"/>
    </Console>
    <RollingFile name="RunFile" fileName="${logDir}/run.log" filePattern="${logDir}/run-%i.log.gz">
      <PatternLayout pattern="${pattern}"/>
      <Policies><SizeBasedTriggeringPolicy size="20 MB"/></Policies>
      <DefaultRolloverStrategy max="5"/>
    </RollingFile>
  </Appenders>

  <Loggers>
    <!-- Keep noisy libraries quiet -->
    <Logger name="org.openqa.selenium" level="warn"/>
    <Logger name="io.netty" level="warn"/>
    <Logger name="org.apache.http" level="warn"/>

    <Root level="info">
      <AppenderRef ref="Console"/>
      <AppenderRef ref="RunFile"/>
    </Root>
  </Loggers>
</Configuration>
```

**Logging rules:** `log.info` for business steps, `log.debug` for technical detail, `log.warn` for retries and fallbacks, `log.error` for failures. **Never** log secrets. **Never** use `System.out.println`.

---

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
| 3 | Browser started inside a Gherkin step ("Given I launch the app") | Order-dependent failures; NPE in `@After` | Driver created in the `@ui` `@Before` hook (§13.2) |
| 4 | `static WebDriver` | Parallel scenarios corrupt each other | `ThreadLocal` `DriverManager` (§7.4) |
| 5 | Mixed test engines / mismatched Cucumber versions | Classpath conflicts, wrong runner | TestNG only; one `${cucumber.version}` (§5) |
| 6 | One giant locator file, or locators hard-coded in Java | Fragile, hard to maintain | One locator file per page, multi-strategy, fail fast, integrity test (§8) |
| 7 | Expected messages hard-coded in steps | Many edits for one wording change | `messages_en.properties` (§9) |
| 8 | Implicit waits and `Thread.sleep` | Slow and flaky | `WaitUtils` explicit waits only (§11.1) |
| 9 | Assertions inside page objects | Pages can't be reused for negative scenarios | Assertions only in steps, with AssertJ descriptions (§12.1) |
| 10 | A "context" class with dozens of public fields | Grows forever, not thread-safe | `TestContext` ThreadLocal map + `ContextKey` (§13.3) |
| 11 | Money compared as `double` | Rounding causes false failures | `BigDecimal` + `isEqualByComparingTo` (§11.4, §14.7) |
| 12 | Test data built through the UI for every scenario | Slow, flaky suites | API-based setup (§14.4) |
| 13 | Hardcoded tags in the runner | Manual edits for every run | `-Dcucumber.filter.tags` (§13.1, §22) |
| 14 | Imperative Gherkin ("When I click…") | Unreadable, brittle scenarios | Declarative business steps (§13.6) |
| 15 | Logs and reports committed | Repo bloat | Everything under `target/` (§18, Appendix A) |
| 16 | Expectations changed to match the app's behaviour | Real defects hidden | `@knownIssue` tag + documented finding (§13.6) |

---

## 21. Coding Standards and Conventions

**Naming**
| Item | Convention | Example |
|---|---|---|
| Packages | lowercase, reverse domain | `com.parag.parabank.pages` |
| Page classes | `<Name>Page` | `TransferFundsPage` |
| Components | `<Name>Component` | `AccountsTableComponent` |
| API clients | `<Resource>Api` | `TransferApi` |
| Step classes | `<Feature><Layer>Steps` | `TransferUiSteps`, `TransferApiSteps` |
| Feature files | kebab-case, one business capability | `transfer-funds.feature` |
| Locator files | kebab-case = page name | `transfer-funds.properties` |
| Locator keys | `<element>.<type>` | `amount.input`, `transfer.button`, `from.account.select` |
| Message keys | `<module>.<context>.<meaning>` | `billpay.validation.payee.required` |
| Constants | UPPER_SNAKE | `ContextKey.CHECKING_ACCOUNT_ID` |

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
mvn clean test -Dcucumber.features="src/test/resources/features/ui/login.feature"

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
| **0. Setup** | 1 day | Repo, `pom.xml`, `.gitignore`, README, folder skeleton | `mvn clean verify` green on Windows and Linux |
| **1. Core** | 3–4 days | ConfigManager, DriverFactory/Manager, BrowserOptions, WaitUtils, ElementActions, Locators, Messages, data readers, exceptions | LocatorIntegrityTest passes |
| **2. BDD + first UI feature** | 2–3 days | Runner, hooks, TestContext; register + login pages, `login.feature` | Login scenarios green, screenshot attached on a forced failure |
| **3. API layer** | 2–3 days | RequestSpecFactory, MaskingLogFilter, clients, models, schemas, TestDataSetup | `@api` transfer scenarios green, no browser started |
| **4. Banking features** | 1 week | Open account, transfer, bill pay, find transactions, loan: UI, API and hybrid scenarios | 15–20 scenarios green in parallel (4 threads) on Chrome and Firefox |
| **5. Reporting and CI** | 2 days | Allure categories/environment, GitHub Actions with ParaBank service container, report on Pages | Green badge, public report link in README |
| **6. Hardening** | ongoing | Known-issue findings documented, dependency upgrades, optional Selenium Grid | Pass rate above 95% without re-runs |

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

### Appendix B: Exception hierarchy
```java
/** All framework errors are unchecked, so method signatures stay clean (no "throws Exception" everywhere). */
public class FrameworkException extends RuntimeException {
    public FrameworkException(String msg) { super(msg); }
    public FrameworkException(String msg, Throwable cause) { super(msg, cause); }
}
public class ConfigException          extends FrameworkException { /* missing or invalid config */ }
public class LocatorNotFoundException extends FrameworkException { /* missing or invalid locator */ }
public class TestDataException        extends FrameworkException { /* missing or invalid data/secret */ }
```

---
*End of design document. Keep it next to the code (`/docs/FRAMEWORK_DESIGN.md`) and update it in the same pull request as any architectural change.*
