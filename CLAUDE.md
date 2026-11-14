# Project instructions for Claude

This repo is a ParaBank UI + API test automation framework (Java 17, Selenium, Cucumber BDD on TestNG, REST Assured, Allure).

## Source of truth
- Follow docs/FRAMEWORK_DESIGN.md for structure, class responsibilities, naming and rules.
- docs/TEST_PLAN.md lists the test cases to automate. Use its IDs (e.g. TRF-02) as Cucumber tags on each scenario (@TRF-02).
- If something in the design doesn't work in practice (wrong ParaBank locator, endpoint or message), tell me and propose the fix. Update the design doc in the same commit as the code change.

## Rules
- Locators go in src/test/resources/locators/<page>.properties, one file per page. Never hardcode locators in Java.
- Expected UI texts go in messages_en.properties. Never hardcode them in steps.
- No Thread.sleep, no implicit waits, no System.out. Explicit waits through Utils/Actions only.
- No assertions in page objects or API clients; assert in steps with AssertJ and a description.
- Money is always BigDecimal.
- No secrets in code or committed files.
- Verify ParaBank locators against the live page and API endpoints against <baseUrl>/services/bank?_wadl before using them.
- If ParaBank behaves differently from an expected banking rule, don't change the expectation. Tag the scenario @knownIssue and tell me.

## Way of working
- Build one roadmap phase at a time (design doc §23). Don't jump ahead.
- After each change: mvn -q compile test-compile must pass. Run the relevant scenarios before committing.
- One commit per logical step, with a clear message. Don't add Co-Authored-By lines.
- At the end of each phase, give me a short summary: what was built, what passed, anything I need to check.
