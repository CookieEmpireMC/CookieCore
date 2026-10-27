package dev.casperrs.cookieCore.moderation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.casperrs.cookieCore.Perm;
import dev.casperrs.cookieCore.moderation.ModerationFixture.TestPlayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ModerationServiceTest {

    private ModerationFixture f;

    @BeforeEach
    void setUp() throws Exception {
        f = new ModerationFixture();
    }

    /** The database write comes first: when it fails nobody is kicked, the ban list is unchanged and the sender hears an error. */
    @Test
    void aFailedWriteKicksNobodyChangesNothingAndTellsTheSender() throws Exception {
        TestPlayer alex = f.player("Alex", Perm.STAFF_BAN);
        TestPlayer steve = f.player("Steve");
        f.service(ModerationFixture.failingInserts(f.store)).ban(alex.player, f.ref(steve), null, false, "griefing");

        assertTrue(steve.online && steve.kicks.isEmpty(), "the target stays connected");
        assertTrue(f.bans.active(steve.id, f.now).isEmpty());
        assertTrue(alex.rec.lastPlain().contains("could not be saved"), alex.rec.lastPlain());
        assertEquals(1, alex.rec.sounds.size(), "the error sound only");
    }

    /** One write in flight per target: a second command on the same player is refused until the first is saved. */
    @Test
    void aSecondCommandOnABusyTargetWaitsForTheFirstWrite() throws Exception {
        TestPlayer alex = f.player("Alex", Perm.STAFF_BAN);
        TestPlayer steve = f.player("Steve");
        f.deferStorage = true;
        assertEquals(1, f.service.ban(alex.player, f.ref(steve), null, false, "griefing"));
        assertEquals(0, f.service.ban(alex.player, f.ref(steve), null, false, "again"));
        assertTrue(alex.rec.lastPlain().contains("is still being saved"), alex.rec.lastPlain());

        f.deferStorage = false;
        f.runDeferred();
        assertEquals(1, f.count("core_punishments"), "one row, not two");
        assertEquals(0, f.service.ban(alex.player, f.ref(steve), null, false, "again"));
        assertTrue(alex.rec.lastPlain().contains("already banned permanently"), alex.rec.lastPlain());
    }

    /** Without a database a kick still happens (logged once to the console); a ban is refused. */
    @Test
    void withoutStorageKicksStillWorkAndBansAreRefused() throws Exception {
        TestPlayer alex = f.player("Alex", Perm.STAFF_BAN);
        TestPlayer steve = f.player("Steve");
        TestPlayer carl = f.player("Carl");
        f.storageUp = false;

        assertEquals(1, f.service.kickOut(alex.player, steve.player, false, "spam"));
        assertEquals(1, steve.kicks.size());
        assertEquals(0, f.service.ban(alex.player, f.ref(carl), null, false, "spam"));
        assertTrue(alex.rec.lastPlain().contains("database is not available"), alex.rec.lastPlain());
        assertTrue(f.bans.active(carl.id, f.now).isEmpty());
        assertEquals(0, f.count("core_punishments"));
        assertTrue(f.log.messages().stream().anyMatch(m -> m.contains("not stored")), f.log.messages().toString());
    }

    /** A public ban goes to everyone online except the target; staff with the notify node also hear the notify sound. */
    @Test
    void aPublicBanIsAnnouncedToEveryoneButTheTarget() throws Exception {
        TestPlayer alex = f.player("Alex", Perm.STAFF_BAN);
        TestPlayer steve = f.player("Steve");
        TestPlayer bob = f.player("Bob");
        TestPlayer carl = f.player("Carl", Perm.STAFF_NOTIFY);

        f.service.ban(alex.player, f.ref(steve), null, false, "griefing");

        assertTrue(bob.rec.plain().stream().anyMatch(m -> m.contains("Steve was banned by Alex")), bob.rec.plain().toString());
        assertTrue(bob.rec.sounds.isEmpty(), "a player hears no alert sound");
        assertTrue(steve.rec.messages.isEmpty(), "the target gets the screen, not the announcement");
        assertTrue(carl.rec.plain().stream().anyMatch(m -> m.contains("was banned by")), carl.rec.plain().toString());
        assertEquals(1, carl.rec.sounds.size());
    }

    /** A silent ban reaches only online staff with the notify node; the sender gets the confirmation alone. */
    @Test
    void aSilentBanReachesOnlyStaffWithTheNotifyNode() throws Exception {
        TestPlayer alex = f.player("Alex", Perm.STAFF_BAN, Perm.STAFF_NOTIFY);
        TestPlayer steve = f.player("Steve");
        TestPlayer bob = f.player("Bob");
        TestPlayer carl = f.player("Carl", Perm.STAFF_NOTIFY);

        f.service.ban(alex.player, f.ref(steve), null, true, "griefing");

        assertTrue(bob.rec.messages.isEmpty() && bob.rec.sounds.isEmpty(), "players see nothing");
        assertTrue(carl.rec.plain().stream().anyMatch(m -> m.startsWith("[silent]") && m.contains("was banned by")),
                carl.rec.plain().toString());
        assertEquals(1, carl.rec.sounds.size());
        assertFalse(alex.rec.plain().stream().anyMatch(m -> m.contains("[silent]")), "the sender is not alerted twice");
    }
}
