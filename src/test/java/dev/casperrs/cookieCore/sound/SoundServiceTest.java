package dev.casperrs.cookieCore.sound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.casperrs.cookieCore.TestSupport;
import dev.casperrs.cookieCore.config.YamlFiles;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;
import net.kyori.adventure.sound.Sound;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class SoundServiceTest {

    private final TestSupport.LogCapture log = new TestSupport.LogCapture();
    private final TestSupport.Recorder heard = new TestSupport.Recorder();
    private final Player player = TestSupport.player(heard);

    private static SoundDef def(String event, String key, String fallback, float volume, float low, float high, String source) {
        return new SoundDef(event, key, fallback, volume, low, high, source);
    }

    /** A generator that always returns the same float. */
    private static RandomGenerator fixed(float value) {
        return new RandomGenerator() {
            @Override
            public long nextLong() {
                return 0;
            }

            @Override
            public float nextFloat() {
                return value;
            }
        };
    }

    @Test
    void playsTheFallbackWhenThePlayerHasNoCustomSounds() {
        SoundService s = new SoundService(log.logger, fixed(0.5f), p -> false);
        s.load(Map.of("e", def("e", "pack:custom", "minecraft:vanilla", 0.5f, 1f, 1f, "ui")));
        s.play(player, "e");
        assertEquals(1, heard.sounds.size());
        Sound sound = heard.sounds.getFirst();
        assertEquals("minecraft:vanilla", sound.name().asString());
        assertEquals(Sound.Source.UI, sound.source());
        assertEquals(0.5f, sound.volume());
        assertEquals(1f, sound.pitch());
    }

    @Test
    void playsTheCustomKeyWhenThePlayerUsesCustomSounds() {
        SoundService s = new SoundService(log.logger, fixed(0.5f), p -> true);
        s.load(Map.of("e", def("e", "pack:custom", "minecraft:vanilla", 1f, 1f, 1f, "master")));
        s.play(player, "e");
        assertEquals("pack:custom", heard.sounds.getFirst().name().asString());
    }

    @Test
    void pitchIsPickedInsideTheRange() {
        SoundService s = new SoundService(log.logger, fixed(0.25f), p -> false);
        s.load(Map.of("e", def("e", "a:b", "a:b", 1f, 1f, 2f, "master")));
        s.play(player, "e");
        assertEquals(1.25f, heard.sounds.getFirst().pitch(), 1e-6);
    }

    @Test
    void unknownEventIsSkippedAndWarnedOnce() {
        SoundService s = new SoundService(log.logger, fixed(0f), p -> false);
        s.load(Map.of());
        s.play(player, "nope");
        s.play(player, "nope");
        assertTrue(heard.sounds.isEmpty());
        assertEquals(1, log.messages().size(), log.messages().toString());
        assertFalse(s.has("nope"));
    }

    @Test
    void consoleHearsNothing() {
        SoundService s = new SoundService(log.logger, fixed(0f), p -> false);
        s.load(Map.of("e", def("e", "a:b", "a:b", 1f, 1f, 1f, "master")));
        TestSupport.Recorder consoleRec = new TestSupport.Recorder();
        s.play(TestSupport.console(consoleRec), "e");
        assertTrue(consoleRec.sounds.isEmpty());
        assertTrue(log.messages().isEmpty());
    }

    @Test
    void shippedSoundsPlayWithoutWarnings() {
        List<String> problems = new ArrayList<>();
        Map<String, SoundDef> defs = SoundConfigParser.parse(YamlFiles.toMap(TestSupport.resourceYaml("sounds.yml")), problems::add);
        SoundService s = new SoundService(log.logger, RandomGenerator.getDefault(), p -> false);
        s.load(defs);
        for (String event : List.of(SoundEvents.UI_CONFIRM, SoundEvents.UI_ERROR, SoundEvents.UI_NOTIFY)) {
            s.play(player, event);
        }
        assertEquals(3, heard.sounds.size());
        assertTrue(log.messages().isEmpty(), log.messages().toString());
        assertTrue(problems.isEmpty());
    }
}
