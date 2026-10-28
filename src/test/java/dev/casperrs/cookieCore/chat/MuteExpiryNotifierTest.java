package dev.casperrs.cookieCore.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.casperrs.cookieCore.chat.ChatFixture.FakePlayer;
import dev.casperrs.cookieCore.moderation.MuteList;
import dev.casperrs.cookieCore.storage.MuteRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class MuteExpiryNotifierTest {

    private static final long NOW = 1_000_000_000_000L;
    private static final long MINUTE = 60_000L;

    private final MuteList mutes = new MuteList();
    private final List<Player> online = new ArrayList<>();
    private final List<Player> notified = new ArrayList<>();
    private final List<Runnable> tasks = new ArrayList<>();
    private final AtomicInteger cancels = new AtomicInteger();
    private final MuteExpiryNotifier notifier;
    private long now = NOW;

    MuteExpiryNotifierTest() {
        mutes.markLoaded();
        notifier = new MuteExpiryNotifier(mutes, () -> now, () -> online, notified::add, (task, period) -> {
            tasks.add(task);
            return cancels::incrementAndGet;
        });
    }

    private static MuteRecord mute(FakePlayer p, Long expiresAt) {
        return new MuteRecord(1L, p.id, p.name, "Staff", "spam", NOW, expiresAt);
    }

    /** A tempmute seen while the player is online is announced once, when it runs out, and never again. */
    @Test
    void aTempmuteThatRunsOutWhileOnlineIsAnnouncedOnce() {
        FakePlayer steve = new FakePlayer("Steve");
        online.add(steve.player);
        mutes.put(mute(steve, NOW + MINUTE));
        notifier.tick();
        now = NOW + 2 * MINUTE;
        notifier.tick();
        notifier.tick();
        assertEquals(List.of(steve.player), notified);
    }

    @Test
    void aPermanentMuteNeverAnnounces() {
        FakePlayer steve = new FakePlayer("Steve");
        online.add(steve.player);
        mutes.put(mute(steve, null));
        notifier.tick();
        now = NOW + 365L * 24 * 60 * MINUTE;
        notifier.tick();
        assertTrue(notified.isEmpty());
    }

    /** An unmute removes the record, so the mute does not "run out" and nothing is announced. */
    @Test
    void unmutingBeforeTheEndSendsNoNotice() {
        FakePlayer steve = new FakePlayer("Steve");
        online.add(steve.player);
        mutes.put(mute(steve, NOW + MINUTE));
        notifier.tick();
        mutes.remove(steve.id);
        now = NOW + 2 * MINUTE;
        notifier.tick();
        assertTrue(notified.isEmpty());
    }

    /** A mute that ran out while the player was offline is not announced when they come back. */
    @Test
    void aMuteThatEndedWhileOfflineIsNotAnnouncedOnJoin() {
        FakePlayer steve = new FakePlayer("Steve");
        mutes.put(mute(steve, NOW - MINUTE));
        online.add(steve.player);
        notifier.tick();
        assertTrue(notified.isEmpty());
    }

    /** The repeating task starts once, and stops itself (and cancels) on the first tick with nobody online. */
    @Test
    void theTaskRunsOnceAndStopsWhenTheServerIsEmpty() {
        notifier.start();
        notifier.start();
        assertEquals(1, tasks.size(), "a second start does not add a task");
        online.clear();
        tasks.get(0).run();
        assertEquals(1, cancels.get());
        notifier.start();
        assertEquals(2, tasks.size(), "the next join starts it again");
    }
}
