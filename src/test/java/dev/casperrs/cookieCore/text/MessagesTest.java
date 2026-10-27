package dev.casperrs.cookieCore.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.casperrs.cookieCore.TestSupport;
import dev.casperrs.cookieCore.storage.BanRecord;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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

    /** Player input (a name, a reason) is never parsed as MiniMessage: a click tag in it shows literally. */
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
    void missingKeyReturnsThePathAndWarnsOnce() {
        messages.load(TestSupport.yaml("a: b\n"));
        assertEquals("no.such.key", messages.raw("no.such.key"));
        assertEquals("no.such.key", plain(messages.component("no.such.key")));
        assertEquals(1, log.messages().size(), "warned once for the same path: " + log.messages());
        assertFalse(messages.has("no.such.key"));
    }

    /** A data-folder copy missing a key falls back to the jar default; the validator still names the gaps. */
    @Test
    void jarDefaultsCoverKeysMissingFromTheDataFolderCopy() {
        YamlConfiguration user = TestSupport.yaml("prefix: \"\"\n");
        user.setDefaults(TestSupport.resourceYaml("messages.yml"));
        messages.load(user);
        assertEquals("You do not have permission to do that.", plain(messages.component("command.no-permission")));
        assertTrue(log.messages().isEmpty());
        List<String> problems = Messages.problems(user);
        assertEquals(Messages.REQUIRED_KEYS.size() - 1, problems.size(), problems.toString());
    }

    @Test
    void nonTextRequiredKeysAreReported() {
        List<String> problems = Messages.problems(TestSupport.yaml("prefix: 5\ncommand:\n  usage:\n    - a\n"));
        assertTrue(problems.stream().anyMatch(p -> p.contains("'prefix'")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("'command.usage'")), problems.toString());
    }

    /** Strict mode throws on unclosed tags, unknown tags and bad nesting; every placeholder the code fills is listed. */
    @Test
    void everyShippedMessageHasEveryRequiredKeyAndParsesStrictly() {
        YamlConfiguration jar = TestSupport.resourceYaml("messages.yml");
        assertTrue(Messages.problems(jar).isEmpty(), Messages.problems(jar).toString());
        MiniMessage strict = MiniMessage.builder().strict(true).build();
        TagResolver all = TagResolver.resolver(
                Placeholder.unparsed("prefix", "P "),
                Placeholder.unparsed("name", "n"), Placeholder.unparsed("input", "i"), Placeholder.unparsed("count", "2"),
                Placeholder.unparsed("problem", "p"), Placeholder.unparsed("version", "1.0.0"),
                Placeholder.unparsed("server", "s"), Placeholder.unparsed("database", "h2"),
                Placeholder.component("storage", Component.text("ok")),
                Placeholder.unparsed("player", "Steve"), Placeholder.unparsed("staff", "Alex"),
                Placeholder.unparsed("level", "5"), Placeholder.component("type", Component.text("walking")),
                Placeholder.component("mode", Component.text("Creative")),
                Placeholder.unparsed("reason", "r"), Placeholder.unparsed("id", "1"), Placeholder.unparsed("remaining", "1m"),
                Placeholder.unparsed("until", "2026-11-03 12:00 UTC"), Placeholder.unparsed("duration", "1d"),
                Placeholder.component("message", Component.text("m")));
        strict.deserialize(jar.getString("prefix"));
        for (String key : jar.getKeys(true)) {
            if (!jar.isString(key) || key.equals("prefix")) {
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

    /** Keys built at runtime ("fly." + state, area + ".no-target"): the literal scan below cannot see them. */
    private static final List<String> BUILT_AT_RUNTIME = List.of(
            "fly.enabled-self", "fly.disabled-self", "fly.enabled-other", "fly.disabled-other", "fly.enabled-by", "fly.disabled-by",
            "speed.set-self", "speed.set-other", "speed.set-by", "speed.reset-self", "speed.reset-other", "speed.reset-by",
            "gamemode.console-needs-target", "gamemode.no-target", "gamemode.no-permission-others",
            "fly.console-needs-target", "fly.no-target", "fly.no-permission-others",
            "speed.console-needs-target", "speed.no-target", "speed.no-permission-others");

    @Test
    void everyMessageKeyUsedInCodeExistsInMessagesYml() throws IOException {
        Pattern literal = Pattern.compile("\"((?:command|scoreboard|gamemode|fly|speed|moderation)\\.(?!yml\"|enabled\")[a-z0-9][a-z0-9.-]*)\"");
        YamlConfiguration jar = TestSupport.resourceYaml("messages.yml");
        Set<String> used = new HashSet<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String code = Files.readString(p).lines()
                        .filter(line -> !line.strip().startsWith("*") && !line.strip().startsWith("//"))
                        .collect(java.util.stream.Collectors.joining("\n"));
                Matcher m = literal.matcher(code);
                while (m.find()) {
                    used.add(m.group(1));
                }
            }
        }
        assertFalse(used.isEmpty(), "found no message keys in code; the scan is broken");
        for (String key : used) {
            assertTrue(jar.isString(key), "code uses message key '" + key + "' but messages.yml has none");
            if (key.startsWith("command.")) {
                assertTrue(Messages.REQUIRED_KEYS.contains(key), "'" + key + "' is used in code but not in Messages.REQUIRED_KEYS");
            }
        }
        for (String key : BUILT_AT_RUNTIME) {
            assertTrue(jar.isString(key), "code builds message key '" + key + "' but messages.yml has none");
        }
    }

    /** Names and reasons are inserted as text: a click tag typed by a player or a staff name shows literally on the screen. */
    @Test
    void banScreenShowsTheReasonAndTheStaffNameUnparsed() {
        messages.load(TestSupport.resourceYaml("messages.yml"));
        BanRecord ban = new BanRecord(7, UUID.randomUUID(), "Steve", "<red>Evil</red>",
                "<click:run_command:'/op me'>spam</click>", 0, null);
        Component screen = messages.banScreen(ban, 0);
        String text = plain(screen);
        assertTrue(text.contains("By: <red>Evil</red>"), text);
        assertTrue(text.contains("Reason: <click:run_command:'/op me'>spam</click>"), text);
        assertTrue(text.contains("Ban ID: #7"), text);
        assertNoClickEvents(screen);
    }

    /** The tempban screen counts the time left from the clock, shows the end in UTC, and TimeText never goes below one second. */
    @Test
    void tempbanScreenShowsTheTimeLeftAndTheEndInUtc() {
        messages.load(TestSupport.resourceYaml("messages.yml"));
        long now = 0;
        long end = Duration.ofDays(6).plusHours(23).toMillis();
        BanRecord ban = new BanRecord(8, UUID.randomUUID(), "Steve", "Alex", "spam", now, end);
        String text = plain(messages.banScreen(ban, now));
        assertTrue(text.contains("Time left: 6d 23h"), text);
        assertTrue(text.contains("Ends: 1970-01-07 23:00 UTC"), text);
        assertEquals("5m 3s", TimeText.remaining(Duration.ofSeconds(303)));
        assertEquals("1s", TimeText.remaining(Duration.ZERO), "never shows zero");
    }
}
