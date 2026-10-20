package dev.casperrs.cookieCore.sound;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Parses {@code sounds.yml}: {@code <event>: {key, fallback, volume, pitch-min, pitch-max, source}}.
 * Bad entries are reported and skipped (or corrected); the rest still load. Pure Java.
 */
public final class SoundConfigParser {

    private static final Pattern KEY = Pattern.compile("([a-z0-9_.-]+:)?[a-z0-9_./-]+");
    static final List<String> SOURCES = List.of("master", "music", "record", "records", "weather", "block",
            "blocks", "hostile", "neutral", "player", "players", "ambient", "voice", "ui");

    private SoundConfigParser() {
    }

    public static Map<String, SoundDef> parse(Map<String, ?> yaml, Consumer<String> problems) {
        Map<String, SoundDef> out = new LinkedHashMap<>();
        for (Map.Entry<String, ?> e : yaml.entrySet()) {
            String event = e.getKey();
            String where = "sounds.yml: '" + event + "'";
            if (!(e.getValue() instanceof Map<?, ?> spec)) {
                problems.accept(where + " must be a section");
                continue;
            }
            String key = text(spec.get("key"));
            String fallback = text(spec.get("fallback"));
            if (key == null || !KEY.matcher(key).matches()) {
                problems.accept(where + " needs a valid 'key', got " + spec.get("key"));
                continue;
            }
            if (fallback == null || !KEY.matcher(fallback).matches()) {
                problems.accept(where + " has no valid 'fallback'; using the key");
                fallback = key;
            }
            String source = spec.get("source") == null ? "master" : String.valueOf(spec.get("source")).toLowerCase(Locale.ROOT);
            if (!SOURCES.contains(source)) {
                problems.accept(where + " has unknown source '" + source + "', using master");
                source = "master";
            }
            float volume = number(spec.get("volume"), 1f);
            float low = number(spec.get("pitch-min"), 1f);
            float high = number(spec.get("pitch-max"), low);
            if (!(volume > 0)) {
                problems.accept(where + " volume must be > 0, using 1.0");
                volume = 1f;
            }
            if (high < low) {
                problems.accept(where + " pitch-max < pitch-min, swapped");
                float t = low;
                low = high;
                high = t;
            }
            out.put(event, new SoundDef(event, key, fallback, volume, low, high, source));
        }
        return out;
    }

    private static String text(Object raw) {
        return raw instanceof String s && !s.isBlank() ? s.trim() : null;
    }

    private static float number(Object raw, float fallback) {
        return raw instanceof Number n && Double.isFinite(n.doubleValue()) ? n.floatValue() : fallback;
    }
}
