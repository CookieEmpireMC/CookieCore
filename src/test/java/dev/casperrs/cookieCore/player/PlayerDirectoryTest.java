package dev.casperrs.cookieCore.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.casperrs.cookieCore.TestSupport;
import dev.casperrs.cookieCore.api.PlayerRef;
import dev.casperrs.cookieCore.storage.PlayerRecord;
import dev.casperrs.cookieCore.storage.PlayerStore;
import dev.casperrs.cookieCore.storage.StorageException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class PlayerDirectoryTest {

    /** In-memory store that records calls and can be told to fail. */
    static final class FakeStore implements PlayerStore {
        final Map<UUID, PlayerRecord> rows = new LinkedHashMap<>();
        final List<String> calls = new ArrayList<>();
        StorageException initFailure;
        RuntimeException initRuntimeFailure;
        boolean failUpserts;
        boolean failFinds;
        int findCalls;
        boolean closed;

        @Override
        public synchronized void init() throws StorageException {
            calls.add("init");
            if (initRuntimeFailure != null) {
                throw initRuntimeFailure;
            }
            if (initFailure != null) {
                throw initFailure;
            }
        }

        @Override
        public synchronized void upsertSeen(UUID uuid, String name, long now) throws StorageException {
            calls.add("upsert:" + name + "@" + now);
            if (failUpserts) {
                throw new StorageException("disk full");
            }
            PlayerRecord old = rows.get(uuid);
            rows.put(uuid, new PlayerRecord(uuid, name, old == null ? now : old.firstSeen(), now));
        }

        @Override
        public synchronized List<PlayerRecord> loadAll() {
            calls.add("loadAll");
            return rows.values().stream().sorted((x, y) -> Long.compare(x.lastSeen(), y.lastSeen())).toList();
        }

        @Override
        public synchronized Optional<PlayerRecord> findByName(String name) throws StorageException {
            calls.add("find:" + name);
            findCalls++;
            if (failFinds) {
                throw new StorageException("db down");
            }
            return rows.values().stream().filter(r -> r.name().equalsIgnoreCase(name))
                    .max((x, y) -> Long.compare(x.lastSeen(), y.lastSeen()));
        }

        @Override
        public synchronized void close() {
            calls.add("close");
            closed = true;
        }

        void put(UUID id, String name, long first, long last) {
            rows.put(id, new PlayerRecord(id, name, first, last));
        }
    }

    /** Collects tasks until {@link #runAll()}, to control the order of io / main work. */
    static final class QueueExecutor implements Executor {
        final Deque<Runnable> queue = new ArrayDeque<>();

        @Override
        public void execute(Runnable task) {
            queue.add(task);
        }

        void runAll() {
            while (!queue.isEmpty()) {
                queue.poll().run();
            }
        }
    }

    private static final Executor DIRECT = Runnable::run;

    private final TestSupport.LogCapture log = new TestSupport.LogCapture();
    private final FakeStore store = new FakeStore();
    private final AtomicLong clock = new AtomicLong(1_000);

    private static UUID id(int n) {
        return new UUID(0, n);
    }

    private PlayerDirectory directory(Executor io, Executor main) {
        return new PlayerDirectory(store, io, main, log.logger, clock::get);
    }

    private PlayerDirectory started() {
        PlayerDirectory d = directory(DIRECT, DIRECT);
        d.start();
        return d;
    }

    // --- start / warm-up -----------------------------------------------------------------------------------------

    @Test
    void startMigratesThenLoadsEveryStoredPlayer() {
        store.put(id(1), "Alice", 1, 10);
        store.put(id(2), "Bob", 2, 20);
        PlayerDirectory d = directory(DIRECT, DIRECT);
        assertFalse(d.storageAvailable());
        CompletableFuture<Void> done = d.start();
        assertTrue(done.isDone());
        assertTrue(d.storageAvailable());
        assertEquals(List.of("init", "loadAll"), store.calls);
        assertEquals(new PlayerRef(id(1), "Alice"), d.cached("alice").orElseThrow());
        assertEquals(new PlayerRef(id(2), "Bob"), d.cached(id(2)).orElseThrow());
        assertEquals(2, d.cachedPlayers().size());
    }

    @Test
    void startRunsOnTheIoExecutorNotTheCaller() {
        QueueExecutor io = new QueueExecutor();
        PlayerDirectory d = directory(io, DIRECT);
        CompletableFuture<Void> done = d.start();
        assertFalse(done.isDone());
        assertTrue(store.calls.isEmpty(), "nothing touched the store before the io thread ran");
        io.runAll();
        assertTrue(done.isDone());
        assertTrue(d.storageAvailable());
    }

    @Test
    void whenTheDatabaseFailsTheDirectoryRunsMemoryOnly() {
        store.initFailure = new StorageException("cannot open");
        PlayerDirectory d = directory(DIRECT, DIRECT);
        CompletableFuture<Void> done = d.start();
        assertTrue(done.isDone() && !done.isCompletedExceptionally(), "start() completes normally even on failure");
        assertFalse(d.storageAvailable());
        assertEquals(1, log.records.size());
        assertEquals(java.util.logging.Level.SEVERE, log.records.getFirst().getLevel());

        d.seen(id(1), "Alice");
        assertEquals(id(1), d.cached("ALICE").orElseThrow().uuid(), "joins still work from memory");
        assertEquals(List.of("init"), store.calls, "no writes while the database is down");
        assertTrue(d.lookup("Zed").join().isEmpty());
        assertEquals(0, store.findCalls, "no database lookups while it is down");
        assertEquals(Optional.of(new PlayerRef(id(1), "Alice")), d.lookup("alice").join(), "memory hits still resolve");
    }

    @Test
    @Disabled("F-04: a RuntimeException from the store (for example a corrupt uuid row in loadAll) escapes start(); on the real single-thread executor it is swallowed silently and storageUp stays false without a log line")
    void anUncheckedStoreFailureAlsoLeavesMemoryOnlyAndIsLogged() {
        store.initRuntimeFailure = new IllegalArgumentException("Invalid UUID string");
        PlayerDirectory d = directory(DIRECT, DIRECT);
        d.start();
        assertFalse(d.storageAvailable());
        assertFalse(log.records.isEmpty(), "the failure must be logged by the plugin");
    }

    // --- case-insensitive lookup, renames ------------------------------------------------------------------------

    @Test
    void lookupIsCaseInsensitiveAndTrimsWhitespace() {
        PlayerDirectory d = started();
        d.seen(id(1), "Notch");
        for (String q : List.of("notch", "NOTCH", "NoTcH", "  notch ")) {
            assertEquals(id(1), d.cached(q).orElseThrow().uuid(), q);
        }
        assertEquals("Notch", d.cached("NOTCH").orElseThrow().name(), "the stored spelling is kept");
        assertTrue(d.cached("notc").isEmpty());
    }

    @Test
    void nullAndBlankNamesAreNotFoundAndDoNotThrow() {
        PlayerDirectory d = started();
        assertTrue(d.cached((String) null).isEmpty());
        assertTrue(d.cached("").isEmpty());
        assertTrue(d.cached("   ").isEmpty());
        assertTrue(d.lookup(null).join().isEmpty());
        assertTrue(d.lookup("  ").join().isEmpty());
        assertEquals(0, store.findCalls);
    }

    @Test
    @Disabled("F-08: cached((UUID) null) throws NullPointerException (ConcurrentHashMap.get(null)); cached((String) null) is null-safe")
    void cachedWithANullUuidIsEmpty() {
        assertTrue(started().cached((UUID) null).isEmpty());
    }

    @Test
    void aRenamedPlayerKeepsTheirIdAndOnlyTheNewNameResolves() {
        PlayerDirectory d = started();
        d.seen(id(1), "OldName");
        d.seen(id(1), "NewName");
        assertTrue(d.cached("oldname").isEmpty());
        assertEquals(id(1), d.cached("newname").orElseThrow().uuid());
        assertEquals("NewName", d.cached(id(1)).orElseThrow().name());
        assertEquals(1, d.cachedPlayers().size());
    }

    @Test
    void aNameTakenOverByAnotherPlayerPointsAtTheNewOwner() {
        PlayerDirectory d = started();
        d.seen(id(1), "Foo");
        d.seen(id(1), "Bar");
        d.seen(id(2), "Foo");
        assertEquals(id(2), d.cached("foo").orElseThrow().uuid());
        assertEquals("Bar", d.cached(id(1)).orElseThrow().name());
        assertEquals(id(1), d.cached("bar").orElseThrow().uuid());
    }

    @Test
    void twoLivePlayersWithTheSameNameDoNotCorruptTheIndex() {
        PlayerDirectory d = started();
        d.seen(id(1), "Foo");
        d.seen(id(2), "Foo"); // stale rename that the old owner has not been seen leaving yet
        d.seen(id(1), "Foo2"); // the first player now renames
        assertEquals(id(2), d.cached("foo").orElseThrow().uuid(), "the later owner keeps the name");
        assertEquals(id(1), d.cached("foo2").orElseThrow().uuid());
        assertEquals(2, d.cachedPlayers().size());
    }

    // --- warm-up vs live joins -----------------------------------------------------------------------------------

    @Test
    void aLiveJoinIsNotOverwrittenByTheWarmUp() {
        store.put(id(1), "OldName", 1, 10);
        QueueExecutor io = new QueueExecutor();
        PlayerDirectory d = directory(io, DIRECT);
        d.start();
        d.seen(id(1), "NewName"); // joins while the warm-up is still queued
        io.runAll();
        assertEquals("NewName", d.cached(id(1)).orElseThrow().name());
        assertTrue(d.cached("oldname").isEmpty(), "the old database name must not come back");
        assertEquals(id(1), d.cached("newname").orElseThrow().uuid());
    }

    @Test
    void aLiveNameIsNotStolenByAnOlderDatabaseRow() {
        store.put(id(2), "Foo", 1, 10); // an old row of somebody who used to be called Foo
        QueueExecutor io = new QueueExecutor();
        PlayerDirectory d = directory(io, DIRECT);
        d.start();
        d.seen(id(1), "Foo"); // the current Foo joins first
        io.runAll();
        assertEquals(id(1), d.cached("foo").orElseThrow().uuid());
    }

    @Test
    void whenTwoStoredPlayersShareANameTheMostRecentWins() {
        store.put(id(1), "Foo", 1, 10);
        store.put(id(2), "foo", 2, 99);
        PlayerDirectory d = started();
        assertEquals(id(2), d.cached("FOO").orElseThrow().uuid());
        assertEquals("Foo", d.cached(id(1)).orElseThrow().name(), "the old player is still reachable by id");
    }

    @Test
    void joinsQueuedBehindTheWarmUpAreSavedAfterIt() {
        QueueExecutor io = new QueueExecutor();
        PlayerDirectory d = directory(io, DIRECT);
        d.start();
        d.seen(id(1), "Alice");
        io.runAll();
        assertEquals(List.of("init", "loadAll", "upsert:Alice@1000"), store.calls);
    }

    // --- persistence of joins ------------------------------------------------------------------------------------

    @Test
    void seenSavesNameAndTimeInTheBackground() {
        QueueExecutor io = new QueueExecutor();
        PlayerDirectory d = directory(io, DIRECT);
        d.start();
        io.runAll();
        clock.set(5_000);
        d.seen(id(1), "Alice");
        assertEquals(List.of("init", "loadAll"), store.calls, "not written synchronously on the caller's thread");
        assertTrue(d.cached("alice").isPresent(), "but already visible in memory");
        io.runAll();
        assertEquals("upsert:Alice@5000", store.calls.getLast());
    }

    @Test
    void aFailingSaveIsLoggedAndDoesNotThrow() {
        PlayerDirectory d = started();
        store.failUpserts = true;
        d.seen(id(1), "Alice");
        assertTrue(d.cached("alice").isPresent());
        var warnings = log.records.stream().filter(r -> r.getLevel() == java.util.logging.Level.WARNING).toList();
        assertEquals(1, warnings.size());
        assertTrue(warnings.getFirst().getMessage().contains("Alice"));
    }

    @Test
    void seenAfterTheIoThreadStoppedStillUpdatesMemory() {
        Executor rejecting = task -> {
            throw new RejectedExecutionException("shut down");
        };
        PlayerDirectory d = directory(rejecting, DIRECT);
        d.seen(id(1), "Alice");
        assertTrue(d.cached("alice").isPresent());
    }

    // --- lookup on a cache miss ----------------------------------------------------------------------------------

    @Test
    void lookupHitsTheStoreOnAMissAndCachesTheResult() {
        PlayerDirectory d = started();
        store.put(id(7), "Remote", 1, 5); // first seen by another server on a shared database
        assertTrue(d.cached("remote").isEmpty());
        Optional<PlayerRef> found = d.lookup("REMOTE").join();
        assertEquals(new PlayerRef(id(7), "Remote"), found.orElseThrow());
        assertEquals(1, store.findCalls);
        assertEquals(id(7), d.cached("remote").orElseThrow().uuid(), "now cached");
        d.lookup("remote").join();
        assertEquals(1, store.findCalls, "the second lookup is answered from memory");
    }

    @Test
    void lookupOfAnUnknownNameIsEmptyAndAsksTheStoreEveryTime() {
        PlayerDirectory d = started();
        assertTrue(d.lookup("ghost").join().isEmpty());
        assertTrue(d.lookup("ghost").join().isEmpty());
        assertEquals(2, store.findCalls, "misses are not negatively cached");
        assertEquals("find:ghost", store.calls.getLast());
    }

    @Test
    void lookupCompletesOnTheMainExecutorNotTheIoThread() {
        QueueExecutor io = new QueueExecutor();
        QueueExecutor main = new QueueExecutor();
        PlayerDirectory d = directory(io, main);
        d.start();
        io.runAll();
        store.put(id(7), "Remote", 1, 5);
        CompletableFuture<Optional<PlayerRef>> f = d.lookup("remote");
        assertFalse(f.isDone());
        io.runAll();
        assertFalse(f.isDone(), "the result is handed to the main executor, not completed on the io thread");
        assertEquals(1, main.queue.size());
        main.runAll();
        assertTrue(f.isDone());
    }

    @Test
    void lookupFailureCompletesExceptionally() {
        PlayerDirectory d = started();
        store.failFinds = true;
        CompletableFuture<Optional<PlayerRef>> f = d.lookup("someone");
        assertTrue(f.isCompletedExceptionally());
        CompletionException e = assertThrows(CompletionException.class, f::join);
        assertTrue(e.getCause() instanceof StorageException);
    }

    @Test
    void lookupDoesNotOverwriteALiveJoinWithTheDatabaseRow() {
        PlayerDirectory d = started();
        store.put(id(1), "OldName", 1, 5);
        store.failUpserts = true; // the save of the rename has not reached the database
        d.seen(id(1), "NewName");
        // the store (stale) still has OldName for this uuid; someone looks the old name up
        Optional<PlayerRef> found = d.lookup("oldname").join();
        assertEquals("NewName", d.cached(id(1)).orElseThrow().name());
        assertTrue(d.cached("oldname").isEmpty(), "old name stays unresolved in the cache");
        assertEquals("NewName", found.orElseThrow().name(), "and the caller gets the live entry");
    }

    @Test
    @Disabled("F-03: when the main executor drops the task (CookieCore.runOnMainThread after disable, or an IllegalPluginAccessException race) the lookup future never completes")
    void lookupFutureStillCompletesWhenTheMainThreadIsGone() {
        Executor dropping = task -> { };
        PlayerDirectory d = directory(DIRECT, dropping);
        d.start();
        store.put(id(7), "Remote", 1, 5);
        assertTrue(d.lookup("remote").isDone(), "a future that never completes leaks the waiting command");
    }

    @Test
    @Disabled("F-03: an exception from the main executor (scheduler refuses a disabled plugin) escapes the io task and is swallowed; the future never completes")
    void lookupFutureCompletesExceptionallyWhenTheMainExecutorRejects() {
        Executor rejecting = task -> {
            throw new RejectedExecutionException("plugin disabled");
        };
        PlayerDirectory d = directory(DIRECT, rejecting);
        d.start();
        store.put(id(7), "Remote", 1, 5);
        CompletableFuture<Optional<PlayerRef>> f = d.lookup("remote");
        assertTrue(f.isDone());
    }

    @Test
    @Disabled("F-03: lookup() after the io executor was shut down throws RejectedExecutionException to the caller instead of returning a failed future")
    void lookupAfterIoShutdownReturnsAFailedFuture() {
        ExecutorService io = Executors.newSingleThreadExecutor();
        PlayerDirectory d = directory(io, DIRECT);
        d.start().join();
        io.shutdown();
        CompletableFuture<Optional<PlayerRef>> f = d.lookup("someone");
        assertTrue(f.isCompletedExceptionally());
    }

    // --- shutdown ------------------------------------------------------------------------------------------------

    @Test
    void shutdownClosesTheStoreBehindThePendingWrites() {
        QueueExecutor io = new QueueExecutor();
        PlayerDirectory d = directory(io, DIRECT);
        d.start();
        io.runAll();
        d.seen(id(1), "Alice");
        d.shutdown();
        assertFalse(store.closed);
        io.runAll();
        assertTrue(store.closed);
        assertEquals(List.of("init", "loadAll", "upsert:Alice@1000", "close"), store.calls);
    }

    @Test
    void shutdownClosesDirectlyWhenTheExecutorIsAlreadyStopped() {
        Executor rejecting = task -> {
            throw new RejectedExecutionException("shut down");
        };
        PlayerDirectory d = directory(rejecting, DIRECT);
        d.shutdown();
        assertTrue(store.closed);
    }

    // --- thread safety -------------------------------------------------------------------------------------------

    @Test
    void concurrentJoinsAndWarmUpKeepTheIndexConsistent() throws Exception {
        int players = 400;
        for (int i = 0; i < players; i++) {
            store.put(id(i), "old" + i, 1, 10); // what the database remembers
        }
        ExecutorService io = Executors.newSingleThreadExecutor();
        ExecutorService joins = Executors.newFixedThreadPool(8);
        try {
            PlayerDirectory d = directory(io, DIRECT);
            CountDownLatch go = new CountDownLatch(1);
            List<CompletableFuture<Void>> work = new ArrayList<>();
            work.add(CompletableFuture.runAsync(() -> {
                await(go);
                d.start().join();
            }));
            for (int t = 0; t < 8; t++) {
                int offset = t;
                work.add(CompletableFuture.runAsync(() -> {
                    await(go);
                    for (int i = offset; i < players; i += 8) {
                        d.seen(id(i), "new" + i);
                        d.cached("old" + i);
                        d.cached(id(i));
                        d.cachedPlayers().size();
                    }
                }, joins));
            }
            go.countDown();
            CompletableFuture.allOf(work.toArray(CompletableFuture[]::new)).get(30, TimeUnit.SECONDS);
            io.shutdown();
            assertTrue(io.awaitTermination(30, TimeUnit.SECONDS));

            for (int i = 0; i < players; i++) {
                assertEquals("new" + i, d.cached(id(i)).orElseThrow().name(), "player " + i);
                assertEquals(id(i), d.cached("NEW" + i).orElseThrow().uuid());
                assertTrue(d.cached("old" + i).isEmpty(), "stale name old" + i + " resurrected by the warm-up");
            }
            assertEquals(players, d.cachedPlayers().size());
        } finally {
            joins.shutdownNow();
            io.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    @Test
    void cachedPlayersIsAReadOnlyView() {
        PlayerDirectory d = started();
        d.seen(id(1), "Alice");
        assertThrows(UnsupportedOperationException.class, () -> d.cachedPlayers().clear());
        assertThrows(UnsupportedOperationException.class, () -> d.cachedPlayers().add(new PlayerRef(id(2), "x")));
        assertEquals("alice".toLowerCase(Locale.ROOT), d.cachedPlayers().iterator().next().name().toLowerCase(Locale.ROOT));
    }

    @Test
    void executionExceptionTypeIsNotLeakedFromJoinHelpers() throws ExecutionException, InterruptedException {
        PlayerDirectory d = started();
        d.seen(id(1), "Alice");
        assertEquals("Alice", d.lookup("alice").get().orElseThrow().name());
    }
}
