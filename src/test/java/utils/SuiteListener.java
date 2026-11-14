package utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.IAlterSuiteListener;
import org.testng.ISuite;
import org.testng.ISuiteListener;
import org.testng.xml.XmlSuite;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Properties;

/**
 * Runs once around the whole suite (registered in META-INF/services, no setup needed):
 * <ul>
 *   <li>-DdpThreads=N sets how many scenarios run in parallel (overrides testng.xml).</li>
 *   <li>Writes the Allure "Environment" box (env, URL, browser…) and the failure categories.</li>
 * </ul>
 */
public class SuiteListener implements IAlterSuiteListener, ISuiteListener {

    private static final Logger LOG = LogManager.getLogger(SuiteListener.class);

    /** -DdpThreads=N → data-provider-thread-count for every suite. */
    @Override
    public void alter(List<XmlSuite> suites) {
        String raw = System.getProperty("dpThreads");
        if (raw == null || raw.isBlank()) {
            return;
        }
        int threads;
        try {
            threads = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new FrameworkException("-DdpThreads must be a positive whole number but was '" + raw + "'", e);
        }
        if (threads < 1) {
            throw new FrameworkException("-DdpThreads must be a positive whole number but was '" + raw + "'");
        }
        for (XmlSuite suite : suites) {
            suite.setDataProviderThreadCount(threads);
            LOG.info(threads == 1 ? "Scenarios run one at a time" : "Up to {} scenarios run at the same time", threads);
        }
    }

    /** Never fails the run: a reporting problem is only logged. */
    @Override
    public void onStart(ISuite suite) {
        LOG.info("Test run on {} ({}) with {}, {} browser", Config.get(Config.BASE_URL), Config.env(),
                Config.get(Config.BROWSER), Config.getBoolean(Config.HEADLESS) ? "headless" : "visible");
        Path results = Path.of(System.getProperty("allure.results.directory", "target/allure-results"));
        try {
            Files.createDirectories(results);
            Properties env = new Properties();
            env.setProperty("Environment", Config.env());
            env.setProperty("Base URL", Config.get(Config.BASE_URL));
            env.setProperty("Browser", Config.get(Config.BROWSER));
            env.setProperty("Headless", Config.get(Config.HEADLESS));
            env.setProperty("Java", System.getProperty("java.version"));
            env.setProperty("OS", System.getProperty("os.name"));
            try (OutputStream out = Files.newOutputStream(results.resolve("environment.properties"))) {
                env.store(out, null);
            }
            try (InputStream in = SuiteListener.class.getClassLoader().getResourceAsStream("allure-categories.json")) {
                if (in != null) {
                    Files.copy(in, results.resolve("categories.json"), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException | RuntimeException e) {
            LOG.warn("Could not write Allure environment: {}", e.getMessage());
        }
    }
}
