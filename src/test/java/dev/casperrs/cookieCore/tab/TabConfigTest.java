package dev.casperrs.cookieCore.tab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.casperrs.cookieCore.TestSupport;
import dev.casperrs.cookieCore.config.YamlFiles;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class TabConfigTest {

    private static final String GOOD = "header:\n  - \"first\"\nfooter: []\nname-format: \"<player>\"\n";

    private final List<String> problems = new ArrayList<>();

    private TabSettings shipped() {
        return TabSettings.parse(YamlFiles.toMap(TestSupport.resourceYaml("tab.yml")), problems::add);
    }

    @Test
    void theShippedTabYmlLoadsWithoutProblemsAndEveryLineIsStrictMiniMessage() {
        TabSettings shipped = shipped();
        assertTrue(problems.isEmpty(), problems.toString());
        MiniMessage strict = MiniMessage.builder().strict(true).build();
        List<String> lines = new ArrayList<>(shipped.header());
        lines.addAll(shipped.footer());
        lines.add(shipped.nameFormat());
        for (String line : lines) {
            strict.deserialize(line);
        }
        CompiledTab.compile(shipped, 0, CompiledTab.BUILT_IN);
    }

    /** A player name is inserted as plain text: a tag in it shows literally and does not colour the name. */
    @Test
    void aPlayerNameWithTagsStaysText() {
        CompiledTab tab = CompiledTab.compile(shipped(), 0, CompiledTab.BUILT_IN);
        Component name = tab.renderName(Map.of(CompiledTab.PLAYER, "<red>Steve"), Map.of(CompiledTab.RANK_PREFIX, Component.empty()));
        assertEquals("<red>Steve", PlainTextComponentSerializer.plainText().serialize(name));
    }

    @Test
    void aMissingNameFormatUsesTheDefaultAndSaysSo() {
        TabSettings settings = TabSettings.parse(Map.of("header", List.of("x")), problems::add);
        assertEquals(TabSettings.DEFAULT_NAME_FORMAT, settings.nameFormat());
        assertEquals(1, problems.size(), problems.toString());
    }

    @Test
    void aBrokenFileOnReloadKeepsTheLastGoodSettingsAndNamesTheProblem() {
        boolean[] broken = {false};
        TabConfig config = new TabConfig(() -> {
            if (broken[0]) {
                throw new YamlFiles.BadYamlException("tab.yml", new IllegalStateException("line 3"));
            }
            return Optional.of(TestSupport.yaml(GOOD));
        }, Optional::empty);
        config.load(problems);
        TabSettings good = config.current();
        assertTrue(problems.isEmpty(), problems.toString());
        broken[0] = true;
        List<String> reload = new ArrayList<>();
        config.load(reload);
        assertSame(good, config.current());
        assertTrue(reload.contains("tab.yml is not valid YAML: line 3"), reload.toString());
        assertTrue(reload.contains("tab.yml: keeping the previous settings"), reload.toString());
    }

    @Test
    void aBrokenFileOnTheFirstLoadFallsBackToTheJarCopy() {
        TabConfig config = new TabConfig(() -> {
            throw new YamlFiles.BadYamlException("tab.yml", new IllegalStateException("bad"));
        }, () -> Optional.of(TestSupport.resourceYaml("tab.yml")));
        config.load(problems);
        assertEquals(shipped(), config.current());
        assertTrue(problems.contains("tab.yml: using the built-in settings"), problems.toString());
    }
}
