package com.parag.framework.listeners;

import com.parag.framework.exceptions.ConfigException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.IAlterSuiteListener;
import org.testng.xml.XmlSuite;

import java.util.List;

/**
 * Overrides suite XML parallelism from the command line: mvn test -DdpThreads=6.
 * Registered through META-INF/services, so no suite XML entry is needed.
 */
public class SuiteTuningListener implements IAlterSuiteListener {

    private static final Logger LOG = LogManager.getLogger(SuiteTuningListener.class);
    private static final String DP_THREADS = "dpThreads";

    @Override
    public void alter(List<XmlSuite> suites) {
        String raw = System.getProperty(DP_THREADS);
        if (raw == null || raw.isBlank()) {
            return;
        }
        int threads;
        try {
            threads = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new ConfigException("-D" + DP_THREADS + " must be a positive integer but was '" + raw + "'", e);
        }
        if (threads < 1) {
            throw new ConfigException("-D" + DP_THREADS + " must be a positive integer but was '" + raw + "'");
        }
        for (XmlSuite suite : suites) {
            suite.setDataProviderThreadCount(threads);
            LOG.info("Suite '{}': data-provider-thread-count set to {}", suite.getName(), threads);
        }
    }
}
