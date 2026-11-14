package runner;

import io.cucumber.testng.AbstractTestNGCucumberTests;
import io.cucumber.testng.CucumberOptions;
import org.testng.annotations.DataProvider;

/**
 * Runs every scenario in src/test/resources/features, one at a time by default (-DdpThreads=N for parallel).
 * Tags are NOT hardcoded here; choose them on the command line:
 * mvn clean test -Dcucumber.filter.tags="@smoke and not @knownIssue"
 */
@CucumberOptions(
        features = "src/test/resources/features",
        glue = {"stepdefinitions", "runner"},
        plugin = {"runner.StepLogger",                                 // readable run log (scenarios, steps, summary)
                  "io.qameta.allure.cucumber7jvm.AllureCucumber7Jvm",   // Allure report for BDD
                  "rerun:target/rerun.txt"},                           // failed scenarios, for a re-run
        monochrome = true
)
public class CucumberTestRunner extends AbstractTestNGCucumberTests {

    /** How many scenarios run at once = data-provider-thread-count in testng.xml (1), or -DdpThreads=N. */
    @Override
    @DataProvider(parallel = true)
    public Object[][] scenarios() {
        return super.scenarios();
    }
}
