package com.parag.framework.locators;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Cache of LocatorRepository objects. Each file is read once and then shared read-only by all threads. */
public final class Locators {

    private static final Map<String, LocatorRepository> CACHE = new ConcurrentHashMap<>();

    private Locators() { }

    /** Locators.forPage("login") reads locators/login.properties (file name == page name). */
    public static LocatorRepository forPage(String pageName) {
        return CACHE.computeIfAbsent(pageName, LocatorRepository::new);
    }
}
