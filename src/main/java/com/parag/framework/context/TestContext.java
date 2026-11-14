package com.parag.framework.context;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-thread key/value store for passing data between steps of ONE scenario,
 * e.g. the account IDs created through the API in a Given step and checked in a Then step.
 * Use {@link ContextKey} constants, not free-text strings. Cleared in the @After hook.
 */
public final class TestContext {

    private static final ThreadLocal<Map<String, Object>> CTX = ThreadLocal.withInitial(HashMap::new);

    private TestContext() { }

    public static void put(String key, Object value) {
        CTX.get().put(key, value);
    }

    /** Fails fast with the key name if a previous step did not store it. */
    @SuppressWarnings("unchecked")
    public static <T> T get(String key) {
        Object v = CTX.get().get(key);
        if (v == null) {
            throw new IllegalStateException("TestContext has no value for '" + key + "'");
        }
        return (T) v;
    }

    public static boolean contains(String key) {
        return CTX.get().containsKey(key);
    }

    public static void clear() {
        CTX.remove();
    }
}
