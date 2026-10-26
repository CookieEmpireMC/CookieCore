package dev.casperrs.cookieCore.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
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
        RuntimeException findRuntimeFailure;
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
            if (findRuntimeFailure != null) {
                throw findRuntimeFailure;
            }
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

    private PlayerDirectory withNames(String... names) {
        PlayerDirectory d = started();
        for (int i = 0; i < names.length; i++) {
            d.seen(id(i + 1), names[i]);
        }
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
        assertEquals(Level.SEVERE, log.records.getFirst().getLevel());

        d.seen(id(1), "Alice");
        assertEquals(id(1), d.cached("ALICE").orElseThrow().uuid(), "joins still work from memory");
        assertEquals(List.of("init"), store.calls, "no writes while the database is down");
        assertTrue(d.lookup("Zed").join().isEmpty());
        assertEquals(0, store.findCalls, "no database lookups while it is down");
    }

    /** F-04: an unchecked exception from the store (a corrupt uuid row) must not escape start(). */
    @Test
    void anUncheckedStoreFailureAlsoLeavesMemoryOnlyAndIsLogged() {
        store.initRuntimeFailure = new IllegalArgumentException("Invalid UUID string");
        PlayerDirectory d = directory(DIRECT, DIRECT);
        d.start();
        assertFalse(d.storageAvailable());
        assertFalse(log.records.isEmpty(), "the failure must be logged by the plugin");
    }

    // --- case-insensitive lookup, renames, takeovers -------------------------------------------------------------

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

    /** F-08: the UUID overload is null-safe like the name overload. */
    @Test
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
    void aFailingSaveIsLoggedAndDoesNotThrow() {
        PlayerDirectory d = started();
        store.failUpserts = true;
        d.seen(id(1), "Alice");
        assertTrue(d.cached("alice").isPresent());
        var warnings = log.records.stream().filter(r -> r.getLevel() == Level.WARNING).toList();
        assertEquals(1, warnings.size());
        assertTrue(warnings.getFirst().getMessage().contains("Alice"));
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

    /** F-03: a lookup future must complete even when the main thread is gone (a waiting command would hang). */
    @Test
    void lookupFutureFailsWithTheMainExecutorsExceptionWhenItRejects() {
        RejectedExecutionException gone = new RejectedExecutionException("CookieCore is disabled");
        Executor rejecting = task -> {
            throw gone;
        };
        PlayerDirectory d = directory(DIRECT, rejecting);
        d.start();
        store.put(id(7), "Remote", 1, 5);
        CompletableFuture<Optional<PlayerRef>> f = d.lookup("remote");
        assertTrue(f.isCompletedExceptionally(), "a future that never completes leaks the waiting command");
        assertSame(gone, assertThrows(CompletionException.class, f::join).getCause());
        assertEquals("Remote", d.cached("remote").orElseThrow().name(), "the row is still cached for the next lookup");
    }

    /** F-03: a scheduler that throws another runtime exception (the isEnabled race) also fails the future. */
    @Test
    void lookupFutureAlsoFailsWhenTheSchedulerThrowsAnotherRuntimeException() {
        Executor scheduler = task -> {
            throw new IllegalStateException("Plugin attempted to register task while disabled");
        };
        PlayerDirectory d = directory(DIRECT, scheduler);
        d.start();
        store.put(id(7), "Remote", 1, 5);
        assertTrue(d.lookup("remote").isCompletedExceptionally());
    }

    @Test
    void lookupAfterIoShutdownReturnsAFailedFuture() {
        ExecutorService io = Executors.newSingleThreadExecutor();
        PlayerDirectory d = directory(io, DIRECT);
        d.start().join();
        io.shutdown();
        CompletableFuture<Optional<PlayerRef>> f = d.lookup("someone");
        assertTrue(f.isCompletedExceptionally());
    }

    // --- writes and shutdown -------------------------------------------------------------------------------------

    /** Joins that were queued before shutdown are written, then the store closes (order matters). */
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

    // --- namesStartingWith (tab completion, F-01) ----------------------------------------------------------------

    @Test
    void namesStartingWithMatchesThePrefixCaseInsensitivelyInAlphabeticalOrder() {
        PlayerDirectory d = withNames("Notch", "NoodleBoy", "nova", "Alice", "Bob");
        assertEquals(List.of("NoodleBoy", "Notch", "nova"), d.namesStartingWith("no", 10));
        assertEquals(List.of("Notch"), d.namesStartingWith("notc", 10));
        assertEquals(List.of("Alice"), d.namesStartingWith("alice", 10), "a full name matches itself");
        assertEquals(List.of(), d.namesStartingWith("alicex", 10));
    }

    @Test
    void namesStartingWithHonoursTheLimitAndReturnsTheFirstInOrder() {
        PlayerDirectory d = withNames("p5", "p3", "p1", "p4", "p2");
        assertEquals(List.of("p1", "p2", "p3"), d.namesStartingWith("p", 3));
        assertEquals(List.of(), d.namesStartingWith("p", 0));
        assertEquals(5, d.namesStartingWith("p", 100).size());
    }

    @Test
    void namesStartingWithFollowsRenamesAndTakeovers() {
        PlayerDirectory d = withNames("OldName", "Other");
        d.seen(id(1), "NewName");
        assertEquals(List.of(), d.namesStartingWith("old", 5), "the old name is gone");
        assertEquals(List.of("NewName"), d.namesStartingWith("new", 5));
        d.seen(id(3), "Other"); // takes the name over from player 2
        assertEquals(List.of("Other"), d.namesStartingWith("oth", 5));
        assertEquals(id(3), d.cached("other").orElseThrow().uuid());
    }

    @Test
    void namesStartingWithTreatsNonAsciiAndPatternCharactersLiterally() {
        PlayerDirectory d = withNames("Zoe", "Émile", "_under", "a.b");
        assertEquals(List.of("Émile"), d.namesStartingWith("é", 5));
        assertEquals(List.of("_under"), d.namesStartingWith("_", 5));
        assertEquals(List.of("a.b"), d.namesStartingWith("a.", 5));
        assertEquals(List.of(), d.namesStartingWith(".*", 5), "no pattern matching");
    }

    @Test
    void namesStartingWithIsSafeWhileJoinsAndRenamesRunConcurrently() throws Exception {
        PlayerDirectory d = started();
        ExecutorService pool = Executors.newFixedThreadPool(5);
        try {
            List<Future<?>> fs = new ArrayList<>();
            for (int w = 0; w < 4; w++) {
                int worker = w;
                fs.add(pool.submit(() -> {
                    for (int i = 0; i < 2_000; i++) {
                        d.seen(id(worker * 10_000 + i % 50), "P" + worker + "_" + (i % 7) + "x" + i % 50);
                    }
                }));
            }
            fs.add(pool.submit(() -> {
                for (int i = 0; i < 2_000; i++) {
                    List<String> names = d.namesStartingWith("p", 200);
                    assertTrue(names.size() <= 200);
                    for (int j = 1; j < names.size(); j++) {
                        assertTrue(names.get(j - 1).compareToIgnoreCase(names.get(j)) <= 0, "sorted");
                    }
                }
            }));
            for (Future<?> f : fs) {
                f.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
