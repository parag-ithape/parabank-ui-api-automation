package Utils;

import java.util.HashMap;
import java.util.Map;

/**
 * Passes data between the steps of ONE scenario, e.g. the account id created in a Given step
 * and checked in a Then step. Each thread has its own store; it is cleared after every scenario.
 *
 * <pre>
 *   ScenarioContext.put(ScenarioContext.CHECKING_ACCOUNT_ID, id);
 *   String id = ScenarioContext.get(ScenarioContext.CHECKING_ACCOUNT_ID);
 * </pre>
 * Use the key constants below; add a new one here instead of typing free text.
 */
public final class ScenarioContext {

    // ---------- Keys ----------
    public static final String CUSTOMER = "customer";
    public static final String CUSTOMER_ID = "customer.id";
    public static final String CHECKING_ACCOUNT_ID = "account.checking.id";
    public static final String SAVINGS_ACCOUNT_ID = "account.savings.id";
    public static final String LAST_RESPONSE = "api.last.response";

    private static final ThreadLocal<Map<String, Object>> STORE = ThreadLocal.withInitial(HashMap::new);

    private ScenarioContext() { }

    public static void put(String key, Object value) {
        STORE.get().put(key, value);
    }

    /** Fails with the key name if no earlier step stored it. */
    @SuppressWarnings("unchecked")
    public static <T> T get(String key) {
        Object value = STORE.get().get(key);
        if (value == null) {
            throw new IllegalStateException("ScenarioContext has no value for '" + key + "'");
        }
        return (T) value;
    }

    public static boolean contains(String key) {
        return STORE.get().containsKey(key);
    }

    public static void clear() {
        STORE.remove();
    }
}
