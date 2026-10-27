package dev.casperrs.cookieCore.text;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Dates and spans shown on the ban screen. UTC and a fixed pattern, because the login runs before the client's locale is
 * known and a server zone would make the text depend on the machine.
 */
public final class TimeText {

    private static final DateTimeFormatter END =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'", Locale.ROOT).withZone(ZoneOffset.UTC);
    private static final long DAY = 86_400;
    private static final long HOUR = 3_600;
    private static final long MINUTE = 60;

    private TimeText() {
    }

    /** {@code 2026-11-03 12:00 UTC}. */
    public static String until(long epochMillis) {
        return END.format(Instant.ofEpochMilli(epochMillis));
    }

    /** The two largest units of the span, rounded down, never shorter than a second: {@code 6d 23h}, {@code 5m 3s}. */
    public static String remaining(Duration span) {
        long s = Math.max(1, span.toSeconds());
        long days = s / DAY;
        long hours = s % DAY / HOUR;
        long minutes = s % HOUR / MINUTE;
        if (days > 0) {
            return days + "d " + hours + "h";
        }
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0) {
            return minutes + "m " + s % MINUTE + "s";
        }
        return s + "s";
    }
}
