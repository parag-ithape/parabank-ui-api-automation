package utils;

import org.testng.annotations.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks locators.properties before any browser starts (no browser needed, under a second).
 * Listed first in testng.xml, so a typo fails the build immediately. All problems are reported together:
 * <ul>
 *   <li>a key that appears twice (in one big file, the second would silently replace the first)</li>
 *   <li>a key without a page section ("page.element.type")</li>
 *   <li>a value that does not parse: unknown strategy, missing ':', empty value</li>
 * </ul>
 */
public class LocatorIntegrityTest {

    @Test(description = "locators.properties has unique keys, a page section on every key, and valid values")
    public void locatorFileIsValid() throws IOException {
        List<String> problems = new ArrayList<>();
        problems.addAll(duplicateKeys());

        assertThat(Locators.pages()).as("pages in %s", Locators.FILE).isNotEmpty();
        for (String page : Locators.pages()) {
            try {
                Locators locators = Locators.forPage(page);
                if (locators.keys().contains("") || locators.keys().isEmpty()) {
                    problems.add("\"" + page + "\" has no page section: keys must look like page.element.type");
                }
                for (String key : locators.keys()) {
                    try {
                        locators.by(key, "x", "y", "z");
                    } catch (FrameworkException | IllegalArgumentException e) {
                        problems.add(e.getMessage());
                    }
                }
            } catch (FrameworkException e) {
                problems.add(e.getMessage());
            }
        }
        assertThat(problems).as("problems in %s", Locators.FILE).isEmpty();
    }

    /** Properties.load() keeps only the last of two equal keys, so duplicates are found by reading the lines. */
    private static List<String> duplicateKeys() throws IOException {
        Map<String, Integer> firstLine = new HashMap<>();
        List<String> problems = new ArrayList<>();
        try (InputStream in = LocatorIntegrityTest.class.getClassLoader().getResourceAsStream(Locators.FILE)) {
            assertThat(in).as("%s on the test classpath", Locators.FILE).isNotNull();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            int number = 0;
            while ((line = reader.readLine()) != null) {
                number++;
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) {
                    continue;
                }
                int separator = trimmed.indexOf('=');
                String key = (separator < 0 ? trimmed : trimmed.substring(0, separator)).trim();
                Integer earlier = firstLine.putIfAbsent(key, number);
                if (earlier != null) {
                    problems.add("Duplicate key \"" + key + "\" on lines " + earlier + " and " + number);
                }
            }
        }
        return problems;
    }
}
