package Utils;

import org.testng.annotations.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Parses EVERY key in EVERY locators/*.properties file. No browser needed, runs in under a second.
 * Listed first in testng.xml, so a typo fails the build before any browser starts.
 * All bad entries are reported together, not just the first one.
 */
public class LocatorIntegrityTest {

    private static final String LOCATOR_DIR = "locators";
    private static final String SUFFIX = ".properties";

    @Test(description = "Every locator file parses and every entry has a known strategy and a value")
    public void allLocatorFilesAreValid() throws IOException, URISyntaxException {
        URL dirUrl = getClass().getClassLoader().getResource(LOCATOR_DIR);
        assertThat(dirUrl).as("'%s' folder on the test classpath", LOCATOR_DIR).isNotNull();

        List<Path> files;
        try (Stream<Path> listing = Files.list(Path.of(dirUrl.toURI()))) {
            files = listing.filter(f -> f.getFileName().toString().endsWith(SUFFIX)).sorted().toList();
        }
        assertThat(files).as("locator files in '%s'", LOCATOR_DIR).isNotEmpty();

        List<String> problems = new ArrayList<>();
        for (Path file : files) {
            String fileName = file.getFileName().toString();
            String page = fileName.substring(0, fileName.length() - SUFFIX.length());
            try {
                Locators repo = Locators.forPage(page);
                if (repo.keys().isEmpty()) {
                    problems.add(page + ": file has no locator entries");
                }
                for (String key : repo.keys()) {
                    try {
                        repo.by(key, "x", "y", "z");
                    } catch (FrameworkException | IllegalArgumentException e) {
                        problems.add(e.getMessage());
                    }
                }
            } catch (FrameworkException e) {
                problems.add(e.getMessage());
            }
        }
        assertThat(problems).as("invalid locator entries in %s", files.stream().map(Path::getFileName).toList())
                .isEmpty();
    }
}
