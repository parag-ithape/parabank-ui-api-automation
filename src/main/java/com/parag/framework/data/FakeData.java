package com.parag.framework.data;

import net.datafaker.Faker;
import net.datafaker.providers.base.Text;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Unique, parallel-safe data.
 *
 * <p>Usernames combine time, thread id and a JVM-wide counter, so two threads (or one thread twice)
 * generating in the same millisecond still get different values.</p>
 */
public final class FakeData {

    private static final Faker FAKER = new Faker();
    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    /** 12 letters/digits with at least one upper case, one lower case and one digit (prefixed "Pw!" adds a symbol). */
    private static final Text.TextRuleConfig PASSWORD_RULE = Text.TextSymbolsBuilder.builder()
            .len(12)
            .with(Text.EN_UPPERCASE, 1)
            .with(Text.EN_LOWERCASE, 1)
            .with(Text.DIGITS, 1)
            .build();

    private FakeData() { }

    public static String username() {
        return "bdd_" + System.currentTimeMillis() + "_" + Thread.currentThread().getId() + "_" + SEQUENCE.incrementAndGet();
    }

    public static String password() {
        return "Pw!" + FAKER.text().text(PASSWORD_RULE);
    }

    public static String firstName() {
        return FAKER.name().firstName();
    }

    public static String lastName() {
        return FAKER.name().lastName();
    }
}
