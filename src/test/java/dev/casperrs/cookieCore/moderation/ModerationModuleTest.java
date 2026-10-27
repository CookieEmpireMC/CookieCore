package dev.casperrs.cookieCore.moderation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.casperrs.cookieCore.storage.BanRecord;
import dev.casperrs.cookieCore.storage.NewPunishment;
import dev.casperrs.cookieCore.storage.PunishmentStore;
import dev.casperrs.cookieCore.storage.PunishmentType;
import dev.casperrs.cookieCore.storage.StorageException;
import java.util.List;
import java.util.UUID;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ModerationModuleTest {

    private ModerationFixture f;

    @BeforeEach
    void setUp() throws Exception {
        f = new ModerationFixture();
    }

    /** A failed start-up load keeps logins closed; the next login queues the retry, and once it works the bans apply. */
    @Test
    void aFailedLoadKeepsLoginsClosedUntilALoginRetriesIt() throws Exception {
        UUID steve = UUID.randomUUID();
        f.store.insert(new NewPunishment(PunishmentType.BAN, steve, "Steve", null, "Console", "griefing", f.now, null, false));
        FlakyLoads store = new FlakyLoads(f.store);
        ModerationModule module = new ModerationModule(f.log.logger, store, Runnable::run, Runnable::run, f.messages, f.sounds,
                f.directory, () -> f.now);
        module.load();
        ModerationListener listener = (ModerationListener) module.listener();

        AsyncPlayerPreLoginEvent refused = ModerationFixture.login(listener, UUID.randomUUID());
        assertEquals(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, refused.getLoginResult());
        assertTrue(ModerationFixture.plain(refused.kickMessage()).contains("starting up"), ModerationFixture.plain(refused.kickMessage()));
        assertEquals(2, store.loads, "the refused login queued one retry");

        assertEquals(AsyncPlayerPreLoginEvent.Result.ALLOWED, ModerationFixture.login(listener, UUID.randomUUID()).getLoginResult());
        assertEquals(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, ModerationFixture.login(listener, steve).getLoginResult());
    }

    /** A store whose first ban load fails, like a database that cannot be reached yet. */
    private static final class FlakyLoads implements PunishmentStore {

        private final PunishmentStore real;
        int loads;

        FlakyLoads(PunishmentStore real) {
            this.real = real;
        }

        @Override
        public long insert(NewPunishment punishment) throws StorageException {
            return real.insert(punishment);
        }

        @Override
        public List<BanRecord> loadActiveBans(long now) throws StorageException {
            if (loads++ == 0) {
                throw new StorageException("database busy");
            }
            return real.loadActiveBans(now);
        }

        @Override
        public int deactivateBans(UUID target, UUID byUuid, String byName, String reason, long now) throws StorageException {
            return real.deactivateBans(target, byUuid, byName, reason, now);
        }
    }
}
