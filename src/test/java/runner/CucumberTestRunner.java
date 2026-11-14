package runner;

import io.cucumber.testng.AbstractTestNGCucumberTests;
import io.cucumber.testng.CucumberOptions;
import org.testng.annotations.DataProvider;

/**
 * Runs every scenario in src/test/resources/features.
 * Tags are NOT hardcoded here; choose them on the command line:
 * mvn clean test -Dcucumber.filter.tags="@smoke and not @knownIssue"
 */
@CucumberOptions(
        features = "src/test/resources/features",
        glue = {"stepdefinitions", "runner"},
        plugin = {"pretty",
                  "io.qameta.allure.cucumber7jvm.AllureCucumber7Jvm",   // Allure report for BDD
                  "rerun:target/rerun.txt"},                           // failed scenarios, for a re-run
        monochrome = true
)
public class CucumberTestRunner extends AbstractTestNGCucumberTests {

    /** parallel = true: scenarios run at the same time; how many = data-provider-thread-count (-DdpThreads=N). */
    @Override
    @DataProvider(parallel = true)
    public Object[][] scenarios() {
        return super.scenarios();
    }
}
