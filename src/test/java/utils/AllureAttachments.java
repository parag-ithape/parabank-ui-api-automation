package utils;

import io.qameta.allure.Allure;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.logging.LogType;

import java.util.stream.Collectors;

/** Extra evidence for failed scenarios. Every method is safe to call even when the browser is broken. */
public final class AllureAttachments {

    private static final Logger LOG = LogManager.getLogger(AllureAttachments.class);

    private AllureAttachments() { }

    public static void pageSource() {
        try {
            Allure.addAttachment("Page source", "text/html", Driver.get().getPageSource(), "html");
        } catch (RuntimeException e) {
            LOG.warn("Could not attach page source: {}", e.getMessage());
        }
    }

    /** Browser console log. Only Chromium browsers support it; elsewhere it is skipped. */
    public static void browserConsoleLogs() {
        try {
            String logs = Driver.get().manage().logs().get(LogType.BROWSER).getAll()
                    .stream().map(Object::toString).collect(Collectors.joining("\n"));
            if (!logs.isBlank()) {
                Allure.addAttachment("Browser console", "text/plain", logs);
            }
        } catch (RuntimeException e) {
            LOG.debug("Browser console log not available: {}", e.getMessage());
        }
    }
}
