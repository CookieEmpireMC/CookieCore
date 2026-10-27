package dev.casperrs.cookieCore.scoreboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.casperrs.cookieCore.TestSupport;
import dev.casperrs.cookieCore.config.YamlFiles;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ScoreboardConfigTest {

    private static final String GOOD = "title: Good\nlines:\n  - a\n";
    private static final String JAR = "title: Jar\nlines:\n  - j\n";

    /** The file the next read returns: a fixed YAML, nothing (missing), or a YAML syntax error. */
    private static final class Disk implements ScoreboardConfig.FileSource {
        Optional<YamlConfiguration> next = Optional.empty();
        boolean broken;

        @Override
        public Optional<YamlConfiguration> read() throws YamlFiles.BadYamlException {
            if (broken) {
                throw new YamlFiles.BadYamlException("scoreboard.yml", new IOException("unexpected end of stream"));
            }
            return next;
        }
    }

    private final Disk disk = new Disk();

    private ScoreboardConfig config() {
        return new ScoreboardConfig(disk, () -> Optional.of(TestSupport.yaml(JAR)));
    }

    /** A broken file on reload (invalid layout or invalid YAML) keeps the previous layout and names the exact problem. */
    @Test
    void aBrokenFileOnReloadKeepsThePreviousLayoutAndSaysWhy() {
        ScoreboardConfig config = config();
        disk.next = Optional.of(TestSupport.yaml(GOOD));
        config.load(new ArrayList<>());

        disk.next = Optional.of(TestSupport.yaml("title: Bad\nlines: []\n"));
        List<String> problems = new ArrayList<>();
        config.load(problems);
        assertTrue(problems.stream().anyMatch(p -> p.contains("'lines' has 0 lines")), problems.toString());
        assertTrue(problems.contains("scoreboard.yml: keeping the previous sidebar"), problems.toString());
        assertEquals("Good", config.current().title());

        disk.broken = true;
        problems.clear();
        config.load(problems);
        assertTrue(problems.stream().anyMatch(p -> p.contains("scoreboard.yml is not valid YAML")), problems.toString());
        assertEquals("Good", config.current().title());
    }

    /** On the very first load a broken file falls back to the jar copy, and the problem is named. */
    @Test
    void aBrokenFileOnFirstLoadFallsBackToTheJarCopyAndNamesTheProblem() {
        disk.next = Optional.of(TestSupport.yaml("lines:\n  - a\n"));
        List<String> problems = new ArrayList<>();
        ScoreboardConfig config = config();
        config.load(problems);
        assertTrue(problems.stream().anyMatch(p -> p.contains("'title' is missing or empty")), problems.toString());
        assertTrue(problems.contains("scoreboard.yml: using the built-in sidebar"), problems.toString());
        assertEquals("Jar", config.current().title());
    }

    /** A missing file is reported, the jar copy is used, and with no jar copy either there is no sidebar (and it says so). */
    @Test
    void aMissingFileIsReportedAndTheJarCopyOrNothingIsUsed() {
        List<String> problems = new ArrayList<>();
        ScoreboardConfig config = config();
        config.load(problems);
        assertTrue(problems.contains("scoreboard.yml is missing"), problems.toString());
        assertTrue(problems.contains("scoreboard.yml: using the built-in sidebar"), problems.toString());
        assertEquals("Jar", config.current().title());

        List<String> none = new ArrayList<>();
        ScoreboardConfig noJar = new ScoreboardConfig(new Disk(), Optional::empty);
        noJar.load(none);
        assertTrue(none.contains("scoreboard.yml: no sidebar will be shown"), none.toString());
        assertNull(noJar.current());
    }
}
