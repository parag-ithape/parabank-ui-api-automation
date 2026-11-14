package com.parag.framework.data;

import com.parag.framework.exceptions.TestDataException;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Replaces ${NAME} with System.getProperty(NAME) or System.getenv(NAME). Fails fast if neither is set.
 * The error names the variable only, never a value.
 */
public final class SecretResolver {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([A-Z0-9_]+)}");

    private SecretResolver() { }

    public static String resolve(String text) {
        Matcher m = PLACEHOLDER.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String name = m.group(1);
            String value = Optional.ofNullable(System.getProperty(name)).orElse(System.getenv(name));
            if (value == null) {
                throw new TestDataException("Secret/env variable not set: " + name);
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
