package com.parag.framework.data;

import net.datafaker.Faker;

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

    private FakeData() { }

    public static String username() {
        return "bdd_" + System.currentTimeMillis() + "_" + Thread.currentThread().getId() + "_" + SEQUENCE.incrementAndGet();
    }

    public static String password() {
        return "Pw!" + FAKER.internet().password(10, 14, true, false, true);
    }

    public static String firstName() {
        return FAKER.name().firstName();
    }

    public static String lastName() {
        return FAKER.name().lastName();
    }
}
