package utils;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.datafaker.Faker;
import net.datafaker.providers.base.Text;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Test data: JSON files from testdata/, secrets from environment variables, and unique generated values.
 *
 * <pre>
 *   TestData.readList("testdata/customers.json", CustomerTemplate.class)
 *   TestData.username()      // bdd_1731..._7_3, unique even in parallel
 *   TestData.password()
 * </pre>
 * JSON may contain ${VAR} placeholders; they are replaced from -DVAR or the VAR environment variable,
 * so no secret is ever committed.
 */
public final class TestData {

    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final Pattern SECRET = Pattern.compile("\\$\\{([A-Z0-9_]+)}");
    private static final Faker FAKER = new Faker();
    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    /** 12 letters/digits with at least one upper case, one lower case and one digit; "Pw!" prefix adds a symbol. */
    private static final Text.TextRuleConfig PASSWORD_RULE = Text.TextSymbolsBuilder.builder()
            .len(12)
            .with(Text.EN_UPPERCASE, 1)
            .with(Text.EN_LOWERCASE, 1)
            .with(Text.DIGITS, 1)
            .build();

    private TestData() { }

    // =============================== JSON files ===============================

    public static <T> List<T> readList(String resource, Class<T> type) {
        try {
            return JSON.readValue(resolveSecrets(readResource(resource)),
                    JSON.getTypeFactory().constructCollectionType(List.class, type));
        } catch (IOException e) {
            throw new FrameworkException("Cannot parse test data: " + resource, e);
        }
    }

    public static <T> T read(String resource, Class<T> type) {
        try {
            return JSON.readValue(resolveSecrets(readResource(resource)), type);
        } catch (IOException e) {
            throw new FrameworkException("Cannot parse test data: " + resource, e);
        }
    }

    /** Replaces ${NAME} with -DNAME or the NAME environment variable. The error names the variable, never a value. */
    public static String resolveSecrets(String text) {
        Matcher m = SECRET.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String name = m.group(1);
            String value = Optional.ofNullable(System.getProperty(name)).orElse(System.getenv(name));
            if (value == null) {
                throw new FrameworkException("Secret/environment variable not set: " + name);
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    // ============================ Generated values ============================

    /** Time + thread id + counter: unique even when threads generate in the same millisecond. */
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

    private static String readResource(String resource) throws IOException {
        try (InputStream in = TestData.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new FrameworkException("Test data not found: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
