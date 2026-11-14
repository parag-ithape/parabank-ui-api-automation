package com.parag.framework.context;

/** Keys for {@link TestContext}. Add a constant here instead of passing a free-text key. */
public final class ContextKey {

    public static final String CUSTOMER = "customer";
    public static final String CUSTOMER_ID = "customer.id";
    public static final String CHECKING_ACCOUNT_ID = "account.checking.id";
    public static final String SAVINGS_ACCOUNT_ID = "account.savings.id";
    public static final String LAST_RESPONSE = "api.last.response";

    private ContextKey() { }
}
