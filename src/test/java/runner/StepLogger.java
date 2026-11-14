package runner;

import io.cucumber.plugin.ConcurrentEventListener;
import io.cucumber.plugin.event.EventPublisher;
import io.cucumber.plugin.event.PickleStepTestStep;
import io.cucumber.plugin.event.Result;
import io.cucumber.plugin.event.Status;
import io.cucumber.plugin.event.TestCase;
import io.cucumber.plugin.event.TestCaseFinished;
import io.cucumber.plugin.event.TestCaseStarted;
import io.cucumber.plugin.event.TestRunFinished;
import io.cucumber.plugin.event.TestRunStarted;
import io.cucumber.plugin.event.TestStepFinished;
import io.cucumber.plugin.event.TestStepStarted;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Writes the run as a readable story: each scenario with its test-plan ID, each Gherkin step,
 * the result with the reason for a failure, and a summary at the end.
 *
 * <pre>
 * SCENARIO  [LOG-01] Registered customer logs in
 * STEP      Given a registered customer
 *             Open https://parabank.parasoft.com/parabank/register.htm      (actions, from the pages)
 * RESULT    PASSED  [LOG-01] Registered customer logs in  (7.2 s)
 * </pre>
 */
public class StepLogger implements ConcurrentEventListener {

    private static final Logger LOG = LogManager.getLogger(StepLogger.class);
    private static final String LINE = "-".repeat(90);

    private final AtomicInteger passed = new AtomicInteger();
    private final AtomicInteger failed = new AtomicInteger();
    private final AtomicInteger skipped = new AtomicInteger();
    private final Queue<String> failures = new ConcurrentLinkedQueue<>();
    private volatile Instant runStart = Instant.now();

    @Override
    public void setEventPublisher(EventPublisher publisher) {
        publisher.registerHandlerFor(TestRunStarted.class, e -> runStart = e.getInstant());
        publisher.registerHandlerFor(TestCaseStarted.class, this::scenarioStarted);
        publisher.registerHandlerFor(TestStepStarted.class, this::stepStarted);
        publisher.registerHandlerFor(TestStepFinished.class, this::stepFinished);
        publisher.registerHandlerFor(TestCaseFinished.class, this::scenarioFinished);
        publisher.registerHandlerFor(TestRunFinished.class, this::runFinished);
    }

    private void scenarioStarted(TestCaseStarted event) {
        LOG.info(LINE);
        LOG.info("SCENARIO  {}", title(event.getTestCase()));
    }

    private void stepStarted(TestStepStarted event) {
        if (event.getTestStep() instanceof PickleStepTestStep step) {
            LOG.info("STEP      {}{}", step.getStep().getKeyword(), step.getStep().getText());
        }
    }

    private void stepFinished(TestStepFinished event) {
        Result result = event.getResult();
        if (result.getStatus() == Status.FAILED) {
            String where = event.getTestStep() instanceof PickleStepTestStep step
                    ? "step \"" + step.getStep().getText() + "\""
                    : "setup/cleanup";
            LOG.error("FAILED    in {}: {}", where, reason(result.getError()));
        } else if (result.getStatus() == Status.UNDEFINED) {
            LOG.error("FAILED    step has no Java code yet (undefined step)");
        }
    }

    private void scenarioFinished(TestCaseFinished event) {
        Status status = event.getResult().getStatus();
        String title = title(event.getTestCase());
        String time = format(event.getResult().getDuration());
        switch (status) {
            case PASSED -> {
                passed.incrementAndGet();
                LOG.info("RESULT    PASSED   {}  ({})", title, time);
            }
            case SKIPPED -> {
                skipped.incrementAndGet();
                LOG.info("RESULT    SKIPPED  {}  ({})", title, time);
            }
            default -> {
                failed.incrementAndGet();
                failures.add(title);
                LOG.error("RESULT    FAILED   {}  ({})", title, time);
            }
        }
    }

    private void runFinished(TestRunFinished event) {
        int total = passed.get() + failed.get() + skipped.get();
        LOG.info(LINE);
        LOG.info("SUMMARY   {} scenarios: {} passed, {} failed, {} skipped  (total time {})",
                total, passed.get(), failed.get(), skipped.get(), format(Duration.between(runStart, event.getInstant())));
        for (String failure : failures) {
            LOG.error("          FAILED: {}", failure);
        }
        LOG.info("REPORTS   Cucumber: {}", Path.of("target", "cucumber-report.html").toAbsolutePath());
        LOG.info("          Allure:   run  mvn allure:serve");
        LOG.info(LINE);
    }

    /** "[LOG-01] Registered customer logs in": the test-plan ID comes from the scenario's tags. */
    private static String title(TestCase testCase) {
        List<String> ids = testCase.getTags().stream().filter(t -> t.matches("@[A-Z]{2,6}-\\d{2,3}")).toList();
        return ids.isEmpty() ? testCase.getName()
                : "[" + String.join(", ", ids).replace("@", "") + "] " + testCase.getName();
    }

    /** First line of the error, e.g. "[Login error] expected: "X" but was: "Y"" in one line. */
    private static String reason(Throwable error) {
        if (error == null) {
            return "unknown reason";
        }
        String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        String oneLine = message.replaceAll("\\s*\\R\\s*", " ").trim();
        return oneLine.length() > 400 ? oneLine.substring(0, 400) + " …" : oneLine;
    }

    private static String format(Duration d) {
        long seconds = d.getSeconds();
        if (seconds < 60) {
            return String.format("%.1f s", d.toMillis() / 1000.0);
        }
        return (seconds / 60) + " min " + (seconds % 60) + " s";
    }
}
