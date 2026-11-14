package com.parag.framework.listeners;

import com.parag.framework.config.ConfigKeys;
import com.parag.framework.config.ConfigManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.ISuite;
import org.testng.ISuiteListener;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/**
 * Writes environment.properties (shown on the Allure overview) and copies allure/categories.json into the
 * results directory before the run starts. Registered through META-INF/services.
 * Never fails the run: a reporting problem is logged as a warning.
 */
public class AllureEnvironmentListener implements ISuiteListener {

    private static final Logger LOG = LogManager.getLogger(AllureEnvironmentListener.class);

    @Override
    public void onStart(ISuite suite) {
        Path results = Path.of(System.getProperty("allure.results.directory", "target/allure-results"));
        try {
            Files.createDirectories(results);
            Properties env = new Properties();
            env.setProperty("Environment", ConfigManager.env());
            env.setProperty("Base URL", ConfigManager.get(ConfigKeys.BASE_URL));
            env.setProperty("Browser", ConfigManager.get(ConfigKeys.BROWSER));
            env.setProperty("Headless", ConfigManager.get(ConfigKeys.HEADLESS));
            env.setProperty("Java", System.getProperty("java.version"));
            env.setProperty("OS", System.getProperty("os.name"));
            try (OutputStream out = Files.newOutputStream(results.resolve("environment.properties"))) {
                env.store(out, null);
            }

            try (InputStream in = AllureEnvironmentListener.class.getClassLoader()
                    .getResourceAsStream("allure/categories.json")) {
                if (in != null) {
                    Files.copy(in, results.resolve("categories.json"), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException | RuntimeException e) {
            LOG.warn("Could not write Allure environment: {}", e.getMessage());
        }
    }
}
