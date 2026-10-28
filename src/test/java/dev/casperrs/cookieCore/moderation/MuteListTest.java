package dev.casperrs.cookieCore.moderation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.casperrs.cookieCore.storage.MuteRecord;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MuteListTest {

    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-00000000000a");

    /** A tempmute is active until its end, then reads as not muted; remove clears it. */
    @Test
    void aTempmuteEndsAtItsExpiryAndRemoveClearsIt() {
        MuteList mutes = new MuteList();
        mutes.put(mute(500L));
        assertTrue(mutes.active(ALEX, 499).isPresent());
        assertTrue(mutes.active(ALEX, 500).isEmpty());

        mutes.put(mute(null));
        assertTrue(mutes.active(ALEX, Long.MAX_VALUE).isPresent(), "a permanent mute never ends");
        mutes.remove(ALEX);
        assertTrue(mutes.active(ALEX, 0).isEmpty());
    }

    /** Two active rows for one player at start-up: the longer lasting one wins, whatever the order. */
    @Test
    void loadKeepsTheLongerLastingMute() {
        MuteList mutes = new MuteList();
        mutes.load(List.of(mute(900L), mute(null), mute(1_000L)));
        assertNull(mutes.active(ALEX, 0).orElseThrow().expiresAt());

        MuteList tempOnly = new MuteList();
        tempOnly.load(List.of(mute(900L), mute(1_000L)));
        assertEquals(1_000L, tempOnly.active(ALEX, 0).orElseThrow().expiresAt());
    }

    private static MuteRecord mute(Long expiresAt) {
        return new MuteRecord(1, ALEX, "Alex", "Console", "spam", 0, expiresAt);
    }
}
