package dev.casperrs.cookieCore.scoreboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.casperrs.cookieCore.TestSupport;
import dev.casperrs.cookieCore.config.YamlFiles;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class ScoreboardLayoutTest {

    private static Optional<ScoreboardLayout> parse(String yaml, List<String> problems) {
        return ScoreboardLayout.parse(YamlFiles.toMap(TestSupport.yaml(yaml)), problems::add);
    }

    /** The shipped file loads without problems, and once the built-in placeholders are filled no tag is left as text (a typo would be). */
    @Test
    void theShippedScoreboardFileParsesCleanlyAndEveryTagIsValid() {
        List<String> problems = new ArrayList<>();
        ScoreboardLayout layout = ScoreboardLayout.parse(YamlFiles.toMap(TestSupport.resourceYaml("scoreboard.yml")), problems::add)
                .orElseThrow(() -> new AssertionError(problems.toString()));
        assertTrue(problems.isEmpty(), problems.toString());

        TagResolver values = TagResolver.resolver(Placeholder.unparsed("player", "P"), Placeholder.unparsed("server", "S"),
                Placeholder.unparsed("online", "1"), Placeholder.unparsed("max", "20"), Placeholder.unparsed("ping", "0"));
        List<String> sources = new ArrayList<>(layout.lines());
        sources.add(layout.title());
        for (String source : sources) {
            String plain = PlainTextComponentSerializer.plainText().serialize(MiniMessage.miniMessage().deserialize(source, values));
            assertFalse(plain.contains("<") || plain.contains(">"), "a tag was left as text in: " + source + " -> " + plain);
        }
    }

    @Test
    void aMissingTitleIsReported() {
        List<String> problems = new ArrayList<>();
        assertFalse(parse("lines:\n  - a\n", problems).isPresent());
        assertTrue(problems.stream().anyMatch(p -> p.contains("'title'")), problems.toString());
    }

    /** Zero lines and sixteen lines are both refused; the message says how many there are. */
    @Test
    void theLineCountMustBeBetweenOneAndFifteen() {
        for (String lines : List.of("lines: []\n", "lines:\n" + "  - a\n".repeat(16))) {
            List<String> problems = new ArrayList<>();
            assertFalse(parse("title: T\n" + lines, problems).isPresent(), lines);
            assertTrue(problems.stream().anyMatch(p -> p.contains("1 to 15")), problems.toString());
        }
    }

    /** A YAML null line (a bare dash) is a blank spacer line, not a crash. */
    @Test
    void aNullLineBecomesBlankText() {
        List<String> problems = new ArrayList<>();
        Optional<ScoreboardLayout> layout = parse("title: T\nlines:\n  -\n  - a\n", problems);
        assertTrue(problems.isEmpty(), problems.toString());
        assertEquals(List.of("", "a"), layout.orElseThrow().lines());
    }
}
