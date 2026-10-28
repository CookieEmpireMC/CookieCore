package dev.casperrs.cookieCore.moderation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.casperrs.cookieCore.Perm;
import dev.casperrs.cookieCore.moderation.ModerationFixture.TestPlayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PunishmentHistoryTest {

    private ModerationFixture f;

    @BeforeEach
    void setUp() throws Exception {
        f = new ModerationFixture();
    }

    /** A page number whose offset would overflow an int is clamped: the page is simply empty, never a storage error. */
    @Test
    void aHugePageNumberShowsTheEmptyHistoryInsteadOfAnError() {
        TestPlayer alex = f.player("Alex", Perm.STAFF_HISTORY);
        TestPlayer steve = f.player("Steve");
        PunishmentHistory history = new PunishmentHistory(f.store, Runnable::run, Runnable::run, f.messages, f.sounds,
                f.log.logger, () -> f.now, () -> true);
        assertEquals(1, history.show(alex.player, f.ref(steve), Integer.MAX_VALUE));
        assertTrue(alex.rec.lastPlain().contains("has no punishment history"), alex.rec.lastPlain());
    }
}
