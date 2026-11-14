package com.parag.framework.config;

/** Every config key used by the framework, so no class passes a magic string to ConfigManager. */
public final class ConfigKeys {

    // Environment
    public static final String BASE_URL = "baseUrl";

    // Browser / execution
    public static final String BROWSER = "browser";
    public static final String EXECUTION_MODE = "execution.mode";
    public static final String HEADLESS = "headless";
    public static final String WINDOW_WIDTH = "window.width";
    public static final String WINDOW_HEIGHT = "window.height";
    public static final String GRID_URL = "grid.url";
    public static final String PAGE_LOAD_STRATEGY = "page.load.strategy";

    // Timeouts
    public static final String TIMEOUT_EXPLICIT = "timeout.explicit";
    public static final String TIMEOUT_PAGELOAD = "timeout.pageload";
    public static final String TIMEOUT_SCRIPT = "timeout.script";
    public static final String TIMEOUT_POLLING_MILLIS = "timeout.polling.millis";

    // Stability
    public static final String CLICK_RETRY_COUNT = "click.retry.count";

    // API
    public static final String API_BASE_PATH = "api.base.path";
    public static final String API_TIMEOUT_SECONDS = "api.timeout.seconds";
    public static final String API_MAX_RESPONSE_MILLIS = "api.max.response.millis";

    // Reporting
    public static final String SCREENSHOT_ON_FAILURE = "screenshot.on.failure";
    public static final String ATTACH_PAGE_SOURCE_ON_FAILURE = "attach.page.source.on.failure";

    // Locale
    public static final String MESSAGES_LOCALE = "messages.locale";

    private ConfigKeys() { }
}
