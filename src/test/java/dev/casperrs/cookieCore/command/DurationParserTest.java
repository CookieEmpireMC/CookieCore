package dev.casperrs.cookieCore.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class DurationParserTest {

    @Test
    void singleUnits() {
        assertEquals(Optional.of(Duration.ofSeconds(45)), DurationParser.parse("45s"));
        assertEquals(Optional.of(Duration.ofMinutes(30)), DurationParser.parse("30m"));
        assertEquals(Optional.of(Duration.ofHours(12)), DurationParser.parse("12h"));
        assertEquals(Optional.of(Duration.ofDays(7)), DurationParser.parse("7d"));
        assertEquals(Optional.of(Duration.ofDays(14)), DurationParser.parse("2w"));
    }

    @Test
    void combinedUnits() {
        assertEquals(Optional.of(Duration.ofDays(7).plusHours(12)), DurationParser.parse("7d12h"));
        assertEquals(Optional.of(Duration.ofHours(1).plusMinutes(30).plusSeconds(15)), DurationParser.parse("1h30m15s"));
        assertEquals(Optional.of(Duration.ofDays(9)), DurationParser.parse("1w2d"));
        assertEquals(Optional.of(Duration.ofDays(1).plusHours(1).plusMinutes(1).plusSeconds(1)), DurationParser.parse("1d1h1m1s"));
    }

    @Test
    void caseWhitespaceAndLeadingZerosAreForgiven() {
        assertEquals(Optional.of(Duration.ofDays(7).plusHours(12)), DurationParser.parse("  7D12H "));
        assertEquals(Optional.of(Duration.ofDays(7)), DurationParser.parse("007d"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "7", "7x", "d", "h30m", "1h2d", "1d1d", "0s", "00m", "0d0h", "-5m", "+5m", "1.5h", "1,5h",
            "7d 12h", "7 d", "5mm", "5ms", "one day", "36501d", "1234567890s", "999999999d", "1d2", "1d12"})
    void rejects(String raw) {
        assertTrue(DurationParser.parse(raw).isEmpty(), "should reject '" + raw + "'");
    }

    @Test
    void upperBoundIsInclusive() {
        assertEquals(Optional.of(Duration.ofDays(36_500)), DurationParser.parse("36500d"));
        assertTrue(DurationParser.parse("36500d1s").isEmpty());
        assertTrue(DurationParser.parse("36501d").isEmpty());
    }

    @Test
    void nineDigitNumbersDoNotOverflow() {
        assertEquals(Optional.of(Duration.ofSeconds(999_999_999L)), DurationParser.parse("999999999s"));
    }
}
