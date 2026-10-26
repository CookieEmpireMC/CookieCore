package dev.casperrs.cookieCore.sound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.sound.Sound;
import org.junit.jupiter.api.Test;

class SoundConfigParserTest {

    private final List<String> problems = new ArrayList<>();

    private static Map<String, Object> entry(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    private Map<String, SoundDef> parse(Map<String, ?> yaml) {
        return SoundConfigParser.parse(yaml, problems::add);
    }

    @Test
    void fullEntryIsReadAndDefaultsApplyToTheRest() {
        var out = parse(Map.of("ui_confirm", entry("key", "minecraft:a.b", "fallback", "minecraft:c.d", "volume", 0.5,
                "pitch-min", 0.8, "pitch-max", 1.2, "source", "UI")));
        assertEquals(new SoundDef("ui_confirm", "minecraft:a.b", "minecraft:c.d", 0.5f, 0.8f, 1.2f, "ui"), out.get("ui_confirm"));
        assertTrue(problems.isEmpty(), problems.toString());

        SoundDef d = parse(Map.of("x", entry("key", "minecraft:a.b", "fallback", "minecraft:a.b", "pitch-min", 1.4))).get("x");
        assertEquals(1f, d.volume());
        assertEquals("master", d.source());
        assertEquals(1.4f, d.pitchMax(), "pitch-max defaults to pitch-min");
    }

    /** A missing or invalid fallback uses the key and reports exactly one problem. */
    @Test
    void missingOrInvalidFallbackUsesTheKeyAndReports() {
        SoundDef missing = parse(Map.of("x", entry("key", "minecraft:a.b"))).get("x");
        assertEquals("minecraft:a.b", missing.fallback());
        SoundDef invalid = parse(Map.of("y", entry("key", "minecraft:a.b", "fallback", "Not A Key!"))).get("y");
        assertEquals("minecraft:a.b", invalid.fallback());
        assertEquals(2, problems.size(), problems.toString());
    }

    @Test
    void badEntriesAreSkippedAndGoodOnesStillLoad() {
        Map<String, Object> yaml = new LinkedHashMap<>();
        yaml.put("not_a_section", "text");
        yaml.put("null_value", null);
        yaml.put("no_key", entry("fallback", "minecraft:a.b"));
        yaml.put("blank_key", entry("key", "  ", "fallback", "minecraft:a.b"));
        yaml.put("bad_key", entry("key", "Has Spaces", "fallback", "minecraft:a.b"));
        yaml.put("numeric_key", entry("key", 5));
        yaml.put("good", entry("key", "minecraft:a.b", "fallback", "minecraft:a.b"));
        var out = parse(yaml);
        assertEquals(List.of("good"), List.copyOf(out.keySet()));
        assertEquals(6, problems.size(), problems.toString());
        assertTrue(problems.stream().allMatch(p -> p.startsWith("sounds.yml: '")));
    }

    @Test
    void unknownSourceFallsBackToMaster() {
        SoundDef d = parse(Map.of("x", entry("key", "a.b", "fallback", "a.b", "source", "loud"))).get("x");
        assertEquals("master", d.source());
        assertEquals(1, problems.size());
    }

    @Test
    void volumeZeroAndNegativeBecomeOneWithAProblem() {
        assertEquals(1f, parse(Map.of("x", entry("key", "a.b", "fallback", "a.b", "volume", 0))).get("x").volume());
        assertEquals(1f, parse(Map.of("y", entry("key", "a.b", "fallback", "a.b", "volume", -3.5))).get("y").volume());
        assertEquals(2, problems.size());
    }

    @Test
    void swappedPitchRangeIsCorrected() {
        SoundDef d = parse(Map.of("x", entry("key", "a.b", "fallback", "a.b", "pitch-min", 1.5, "pitch-max", 0.5))).get("x");
        assertEquals(0.5f, d.pitchMin());
        assertEquals(1.5f, d.pitchMax());
        assertEquals(1, problems.size());
    }

    @Test
    void nanAndInfinityAreIgnored() {
        SoundDef d = parse(Map.of("x", entry("key", "a.b", "fallback", "a.b", "volume", Double.NaN,
                "pitch-min", Double.POSITIVE_INFINITY, "pitch-max", Double.NEGATIVE_INFINITY))).get("x");
        assertEquals(1f, d.volume());
        assertEquals(1f, d.pitchMin());
        assertEquals(1f, d.pitchMax());
    }

    @Test
    void sourceNamesMapToRealSoundSources() {
        assertEquals(Sound.Source.RECORD, SoundService.source("records"));
        assertEquals(Sound.Source.BLOCK, SoundService.source("blocks"));
        assertEquals(Sound.Source.PLAYER, SoundService.source("players"));
        assertEquals(Sound.Source.UI, SoundService.source("ui"));
        assertEquals(Sound.Source.MASTER, SoundService.source("nonsense"));
    }
}
