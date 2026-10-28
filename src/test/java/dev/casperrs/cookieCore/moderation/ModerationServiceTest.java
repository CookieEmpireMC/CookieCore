package dev.casperrs.cookieCore.moderation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.casperrs.cookieCore.Perm;
import dev.casperrs.cookieCore.moderation.ModerationFixture.TestPlayer;
import dev.casperrs.cookieCore.storage.BanRecord;
import dev.casperrs.cookieCore.storage.PunishmentStore;
import dev.casperrs.cookieCore.storage.StorageException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
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

    /** An Error on the storage thread still frees the target and tells the sender; the next command on the same player goes through. */
    @Test
    void anErrorOnTheStorageThreadFreesTheTargetAndTellsTheSender() throws Exception {
        TestPlayer alex = f.player("Alex", Perm.STAFF_BAN);
        TestPlayer steve = f.player("Steve");
        ErrorOnce store = new ErrorOnce(f.store);
        ModerationService service = f.service(store);

        assertThrows(InternalError.class, () -> service.ban(alex.player, f.ref(steve), null, false, "griefing"));
        assertTrue(alex.rec.lastPlain().contains("could not be saved"), alex.rec.lastPlain());
        assertEquals(1, service.ban(alex.player, f.ref(steve), null, false, "griefing"));
        assertEquals(1, f.count("core_punishments"));
    }

    /** Its first insert throws an Error, the way a broken storage thread would; everything else is the real store. */
    private static final class ErrorOnce implements PunishmentStore {

        private final PunishmentStore real;
        private boolean failNext = true;

        ErrorOnce(PunishmentStore real) {
            this.real = real;
        }

        @Override
        public long insert(dev.casperrs.cookieCore.storage.NewPunishment punishment) throws StorageException {
            if (failNext) {
                failNext = false;
                throw new InternalError("simulated storage failure");
            }
            return real.insert(punishment);
        }

        @Override
        public List<BanRecord> loadActiveBans(long now) throws StorageException {
            return real.loadActiveBans(now);
        }

        @Override
        public int deactivateBans(UUID target, UUID byUuid, String byName, String reason, long now) throws StorageException {
            return real.deactivateBans(target, byUuid, byName, reason, now);
        }
    }

    /** A mute is written first and only then takes effect: a failed write leaves the mute list and the target's chat alone. */
    @Test
    void aFailedMuteWriteChangesNothing() throws Exception {
        TestPlayer alex = f.player("Alex", Perm.STAFF_MUTE);
        TestPlayer steve = f.player("Steve");
        f.service(ModerationFixture.failingInserts(f.store)).mute(alex.player, f.ref(steve), null, false, "spam");

        assertTrue(f.mutes.active(steve.id, f.now).isEmpty());
        assertTrue(alex.rec.lastPlain().contains("could not be saved"), alex.rec.lastPlain());
    }

    /** A round trip stores and clears the mute; self, already muted and not muted are refused without a change. */
    @Test
    void muteAndUnmuteRoundTripAndRefuseTheImpossible() throws Exception {
        TestPlayer alex = f.player("Alex", Perm.STAFF_MUTE, Perm.STAFF_UNMUTE);
        TestPlayer steve = f.player("Steve");
        assertEquals(0, f.service.mute(alex.player, f.ref(alex), null, false, "spam"), "self");
        assertEquals(0, f.service.unmute(alex.player, f.ref(steve), false, null), "not muted");

        assertEquals(1, f.service.mute(alex.player, f.ref(steve), Duration.ofHours(1), false, "spam"));
        assertTrue(f.mutes.active(steve.id, f.now).isPresent());
        assertEquals(1, f.store.loadActiveMutes(f.now).size());
        assertEquals(0, f.service.mute(alex.player, f.ref(steve), null, false, "again"), "already muted");

        assertEquals(1, f.service.unmute(alex.player, f.ref(steve), false, "appealed"));
        assertTrue(f.mutes.active(steve.id, f.now).isEmpty());
        assertTrue(f.store.loadActiveMutes(f.now).isEmpty());
    }
}
