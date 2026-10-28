package dev.casperrs.cookieCore.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.casperrs.cookieCore.TestSupport;
import dev.casperrs.cookieCore.config.YamlFiles;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ChatConfigTest {

    /** What the next read of the admin's file returns; set by each test. */
    private Optional<YamlConfiguration> file = Optional.empty();
    private YamlFiles.BadYamlException broken;

    private ChatConfig config() {
        return new ChatConfig(() -> {
            if (broken != null) {
                throw broken;
            }
            return file;
        }, () -> Optional.of(TestSupport.resourceYaml("chat.yml")));
    }

    /** A first load that cannot read the file uses the jar's copy, and says so. */
    @Test
    void firstLoadWithABrokenFileFallsBackToTheJarCopy() {
        broken = new YamlFiles.BadYamlException("chat.yml", new IllegalStateException("bad indent"));
        ChatConfig config = config();
        List<String> problems = new ArrayList<>();
        config.load(problems);
        assertNotNull(config.current());
        assertEquals(5, config.current().ranks().size(), "the jar copy's ranks");
        assertTrue(problems.stream().anyMatch(p -> p.contains("chat.yml") && p.contains("bad indent")), problems.toString());
        assertTrue(problems.contains("chat.yml: using the built-in settings"), problems.toString());
    }

    /** A reload that breaks a required part keeps the last good settings and names the missing part. */
    @Test
    void aReloadWithAMissingPartKeepsTheLastGoodSettings() {
        file = Optional.of(TestSupport.resourceYaml("chat.yml"));
        ChatConfig config = config();
        config.load(new ArrayList<>());
        ChatSettings good = config.current();

        YamlConfiguration bad = TestSupport.yaml("staff-format: '<message>'\nformat: ''\n");
        file = Optional.of(bad);
        List<String> problems = new ArrayList<>();
        config.load(problems);
        assertSame(good, config.current());
        assertEquals(List.of("chat.yml: 'format' is missing or empty", "chat.yml: keeping the previous settings"), problems);
    }

    /** A reload with a file that is not valid YAML keeps the last good settings and reports the file. */
    @Test
    void aReloadWithBrokenYamlKeepsTheLastGoodSettings() {
        file = Optional.of(TestSupport.resourceYaml("chat.yml"));
        ChatConfig config = config();
        config.load(new ArrayList<>());
        ChatSettings good = config.current();

        broken = new YamlFiles.BadYamlException("chat.yml", new IllegalStateException("unclosed quote"));
        List<String> problems = new ArrayList<>();
        config.load(problems);
        assertSame(good, config.current());
        assertTrue(problems.stream().anyMatch(p -> p.contains("unclosed quote")), problems.toString());
        assertTrue(problems.contains("chat.yml: keeping the previous settings"), problems.toString());
    }
}
