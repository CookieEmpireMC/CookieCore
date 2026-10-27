package dev.casperrs.cookieCore.moderation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.casperrs.cookieCore.TestSupport;
import dev.casperrs.cookieCore.storage.BanRecord;
import dev.casperrs.cookieCore.text.Messages;
import java.util.UUID;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The login decision: memory only, so the test never needs a database. */
class ModerationListenerTest {

    private static final long NOW = 1_000_000L;
    private final BanList bans = new BanList();
    private final Messages messages = new Messages(new TestSupport.LogCapture().logger);
    private final ModerationListener listener = new ModerationListener(bans, messages, () -> NOW);

    @BeforeEach
    void setUp() {
        messages.load(TestSupport.resourceYaml("messages.yml"));
    }

    private AsyncPlayerPreLoginEvent login(UUID who) {
        return ModerationFixture.login(listener, who);
    }

    /** A permanent ban and a running tempban are refused with their screen; an expired tempban gets in. */
    @Test
    void activeBansAreRefusedWithTheScreenAndAnExpiredTempbanGetsIn() {
        UUID permanent = UUID.randomUUID();
        UUID timed = UUID.randomUUID();
        UUID expired = UUID.randomUUID();
        bans.markLoaded();
        bans.put(new BanRecord(1, permanent, "Steve", "Alex", "griefing", NOW - 5, null));
        bans.put(new BanRecord(2, timed, "Steve", "Alex", "spam", NOW - 5, NOW + 60_000));
        bans.put(new BanRecord(3, expired, "Steve", "Alex", "old", NOW - 5, NOW - 1));

        AsyncPlayerPreLoginEvent refused = login(permanent);
        assertEquals(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, refused.getLoginResult());
        String screen = ModerationFixture.plain(refused.kickMessage());
        assertTrue(screen.contains("You are banned from this server") && screen.contains("griefing"), screen);

        AsyncPlayerPreLoginEvent tempban = login(timed);
        assertEquals(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, tempban.getLoginResult());
        assertTrue(ModerationFixture.plain(tempban.kickMessage()).contains("Time left: 1m 0s"), ModerationFixture.plain(tempban.kickMessage()));

        assertEquals(AsyncPlayerPreLoginEvent.Result.ALLOWED, login(expired).getLoginResult());
        assertEquals(AsyncPlayerPreLoginEvent.Result.ALLOWED, login(UUID.randomUUID()).getLoginResult());
    }

    /** Until the start-up load has run nobody is checked, so a banned player cannot slip in during start-up. */
    @Test
    void nobodyGetsInBeforeTheBansAreLoaded() {
        AsyncPlayerPreLoginEvent event = login(UUID.randomUUID());
        assertEquals(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, event.getLoginResult());
        assertTrue(ModerationFixture.plain(event.kickMessage()).contains("starting up"), ModerationFixture.plain(event.kickMessage()));
    }
}
