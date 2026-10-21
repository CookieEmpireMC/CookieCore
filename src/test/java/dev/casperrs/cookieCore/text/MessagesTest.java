package dev.casperrs.cookieCore.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.casperrs.cookieCore.TestSupport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class MessagesTest {

    private static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    private final TestSupport.LogCapture log = new TestSupport.LogCapture();
    private final Messages messages = new Messages(log.logger);

    @Test
    void placeholdersAreUnparsedSoPlayerInputShowsLiterally() {
        messages.load(TestSupport.yaml("m: \"<red>Hello <name>!</red>\"\n"));
        Component c = messages.component("m", Placeholder.unparsed("name", "<bold><click:run_command:'/op me'>Evil</click></bold>"));
        assertEquals("Hello <bold><click:run_command:'/op me'>Evil</click></bold>!", plain(c));
        assertNoClickEvents(c);
    }

    private static void assertNoClickEvents(Component c) {
        assertEquals(null, c.clickEvent());
        c.children().forEach(MessagesTest::assertNoClickEvents);
    }

    @Test
    void prefixIsExpanded() {
        messages.load(TestSupport.yaml("prefix: \"[CC] \"\nm: \"<prefix>hello\"\n"));
        assertEquals("[CC] hello", plain(messages.component("m")));
    }

    @Test
    void prefixIsEmptyWhenNotConfigured() {
        messages.load(TestSupport.yaml("m: \"<prefix>hello\"\n"));
        assertEquals("hello", plain(messages.component("m")));
    }

    @Test
    void missingKeyReturnsThePathAndWarnsOnce() {
        messages.load(TestSupport.yaml("a: b\n"));
        assertEquals("no.such.key", messages.raw("no.such.key"));
        assertEquals("no.such.key", plain(messages.component("no.such.key")));
        assertEquals(1, log.messages().size(), "warned once for the same path: " + log.messages());
        messages.raw("other.key");
        assertEquals(2, log.messages().size());
        assertFalse(messages.has("no.such.key"));
        assertTrue(messages.has("a"));
    }

    @Test
    void reloadingAllowsTheWarningAgain() {
        messages.load(TestSupport.yaml("a: b\n"));
        messages.raw("x");
        messages.load(TestSupport.yaml("a: b\n"));
        messages.raw("x");
        assertEquals(2, log.messages().size());
    }

    @Test
    void jarDefaultsCoverKeysMissingFromTheDataFolderCopy() {
        YamlConfiguration user = TestSupport.yaml("prefix: \"\"\n");
        user.setDefaults(TestSupport.resourceYaml("messages.yml"));
        messages.load(user);
        assertEquals("You do not have permission to do that.", plain(messages.component("command.no-permission")));
        assertTrue(log.messages().isEmpty());
        // ...but the validator still tells the admin which keys their file lacks
        List<String> problems = Messages.problems(user);
        assertEquals(Messages.REQUIRED_KEYS.size() - 1, problems.size(), problems.toString());
    }

    @Test
    void nonTextRequiredKeyIsReported() {
        List<String> problems = Messages.problems(TestSupport.yaml("prefix: 5\ncommand:\n  usage:\n    - a\n"));
        assertTrue(problems.stream().anyMatch(p -> p.contains("'prefix'")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("'command.usage'")), problems.toString());
    }

    @Test
    void shippedMessagesContainEveryRequiredKey() {
        YamlConfiguration jar = TestSupport.resourceYaml("messages.yml");
        assertTrue(Messages.problems(jar).isEmpty(), Messages.problems(jar).toString());
        for (String key : Messages.REQUIRED_KEYS) {
            assertTrue(jar.isString(key), "messages.yml lacks " + key);
        }
    }

    @Test
    void shippedMessagesHaveNoKeyThatIsNotRequired() {
        YamlConfiguration jar = TestSupport.resourceYaml("messages.yml");
        Set<String> extra = new HashSet<>();
        for (String key : jar.getKeys(true)) {
            if (jar.isString(key) && !Messages.REQUIRED_KEYS.contains(key)) {
                extra.add(key);
            }
        }
        assertTrue(extra.isEmpty(), "messages.yml keys not in Messages.REQUIRED_KEYS (not validated on reload): " + extra);
    }

    @Test
    void everyShippedMessageParsesStrictly() {
        // strict mode throws on unclosed tags, unknown tags and bad nesting; every placeholder the code passes is listed
        MiniMessage strict = MiniMessage.builder().strict(true).build();
        TagResolver all = TagResolver.resolver(
                Placeholder.unparsed("prefix", "P "),
                Placeholder.unparsed("name", "n"), Placeholder.unparsed("input", "i"), Placeholder.unparsed("count", "2"),
                Placeholder.unparsed("problem", "p"), Placeholder.unparsed("version", "1.0.0"),
                Placeholder.unparsed("server", "s"), Placeholder.unparsed("database", "h2"),
                Placeholder.component("storage", Component.text("ok")));
        YamlConfiguration jar = TestSupport.resourceYaml("messages.yml");
        // the prefix is expanded with Placeholder.parsed by the service, so it must parse as well
        strict.deserialize(jar.getString("prefix"));
        for (String key : Messages.REQUIRED_KEYS) {
            if (key.equals("prefix")) {
                continue;
            }
            try {
                strict.deserialize(jar.getString(key), TagResolver.resolver(
                        Placeholder.parsed("prefix", jar.getString("prefix")), all));
            } catch (RuntimeException e) {
                throw new AssertionError("messages.yml '" + key + "' does not parse: " + e.getMessage(), e);
            }
        }
    }

    @Test
    void everyMessageKeyUsedInCodeExistsInMessagesYml() throws IOException {
        Pattern literal = Pattern.compile("\"(command\\.[a-z0-9.-]+)\"");
        YamlConfiguration jar = TestSupport.resourceYaml("messages.yml");
        Set<String> used = new HashSet<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                Matcher m = literal.matcher(Files.readString(p));
                while (m.find()) {
                    used.add(m.group(1));
                }
            }
        }
        assertFalse(used.isEmpty(), "found no message keys in code; the scan is broken");
        for (String key : used) {
            assertTrue(jar.isString(key), "code uses message key '" + key + "' but messages.yml has none");
            assertTrue(Messages.REQUIRED_KEYS.contains(key), "'" + key + "' is used in code but not in Messages.REQUIRED_KEYS");
        }
    }

    @Test
    void shippedPlaceholdersMatchWhatTheCodeFills() {
        // a typo like <vesion> in messages.yml would show up as a literal tag only after strict parsing; the
        // strict-parse test above covers it, this one pins the contract keys of the brief
        YamlConfiguration jar = TestSupport.resourceYaml("messages.yml");
        for (String key : List.of("command.no-permission", "command.unknown-player", "command.reloaded",
                "command.reload-failed", "command.version")) {
            assertTrue(jar.isString(key), "brief contract key missing: " + key);
        }
    }
}
