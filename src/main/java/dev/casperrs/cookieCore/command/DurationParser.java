package dev.casperrs.cookieCore.command;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

/**
 * Parses real-time durations such as {@code 7d12h}, {@code 30m}, {@code 1w2d} or {@code 1h30m15s}. Pure Java.
 *
 * <p>Units: {@code w} (7 days), {@code d}, {@code h}, {@code m}, {@code s}. Each unit may appear once, from large to
 * small, with no spaces; the result must be longer than zero. A bare number is rejected on purpose (seconds or
 * minutes would be a guess). Not Minecraft's tick-based {@code time()} argument, where {@code d} means in-game days.
 */
public final class DurationParser {

    /** Longest accepted duration (about 100 years); keeps the arithmetic far from overflow. */
    static final Duration MAX = Duration.ofDays(36_500);

    private static final String UNITS = "wdhms";
    private static final long[] SECONDS = {7 * 86_400L, 86_400L, 3_600L, 60L, 1L};

    private DurationParser() {
    }

    /** The duration, or empty when {@code raw} is not a valid duration. */
    public static Optional<Duration> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) {
            return Optional.empty();
        }
        long total = 0;
        int lastUnit = -1;
        int i = 0;
        while (i < s.length()) {
            int start = i;
            while (i < s.length() && s.charAt(i) >= '0' && s.charAt(i) <= '9') {
                i++;
            }
            if (i == start || i == s.length() || i - start > 9) {
                return Optional.empty(); // no digits, no unit after the digits, or an absurdly long number
            }
            int unit = UNITS.indexOf(s.charAt(i));
            if (unit < 0 || unit <= lastUnit) {
                return Optional.empty(); // unknown unit, repeated unit, or out of order (1h2d)
            }
            lastUnit = unit;
            total += Long.parseLong(s.substring(start, i)) * SECONDS[unit];
            i++;
            if (total > MAX.toSeconds()) {
                return Optional.empty();
            }
        }
        return total > 0 ? Optional.of(Duration.ofSeconds(total)) : Optional.empty();
    }
}
