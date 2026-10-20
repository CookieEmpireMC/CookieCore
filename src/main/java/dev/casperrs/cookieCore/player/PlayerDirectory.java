package dev.casperrs.cookieCore.player;

import dev.casperrs.cookieCore.api.PlayerLookup;
import dev.casperrs.cookieCore.api.PlayerRef;
import dev.casperrs.cookieCore.storage.PlayerRecord;
import dev.casperrs.cookieCore.storage.PlayerStore;
import dev.casperrs.cookieCore.storage.StorageException;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Who has joined this server before: a case-insensitive name to UUID cache over the {@code players} table.
 *
 * <ul>
 *   <li>Reads ({@link #cached}, {@link #cachedPlayers}) only touch memory, so commands and suggestions on the main
 *       thread never wait for the database.</li>
 *   <li>Every database call (warm-up, saving a join, {@link #lookup} misses) runs on the single storage thread
 *       ({@code io}); results come back through {@code main}.</li>
 *   <li>If the database is unavailable the directory keeps working from memory for the players seen since the
 *       restart and logs the problem once.</li>
 * </ul>
 */
public final class PlayerDirectory implements PlayerLookup {

    private final PlayerStore store;
    private final Executor io;
    private final Executor main;
    private final Logger log;
    private final LongSupplier clock;

    private final ConcurrentHashMap<String, PlayerRef> byName = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, PlayerRef> byId = new ConcurrentHashMap<>();
    /** Players whose entry came from a live join; the warm-up must not overwrite them with older database rows. */
    private final Set<UUID> live = new HashSet<>();
    private volatile boolean storageUp;

    /**
     * @param io   the single storage thread
     * @param main runs a task on the server main thread (results of {@link #lookup})
     */
    public PlayerDirectory(PlayerStore store, Executor io, Executor main, Logger log, LongSupplier clock) {
        this.store = store;
        this.io = io;
        this.main = main;
        this.log = log;
        this.clock = clock;
    }

    /**
     * Opens the database (schema migration) and loads every stored player into the cache, on the storage thread.
     * The future completes normally even when the database fails (the directory then runs memory-only); the failure is
     * logged. Call before registering listeners; joins that arrive earlier are queued behind it.
     */
    public CompletableFuture<Void> start() {
        CompletableFuture<Void> done = new CompletableFuture<>();
        io.execute(() -> {
            try {
                store.init();
                List<PlayerRecord> all = store.loadAll();
                for (PlayerRecord record : all) {
                    merge(record);
                }
                storageUp = true;
                log.info("Loaded " + all.size() + " known player(s)");
            } catch (StorageException e) {
                log.log(Level.SEVERE, "Database unavailable: player names are kept in memory only until the next restart", e);
            } finally {
                done.complete(null);
            }
        });
        return done;
    }

    /** Queues closing the database after the pending writes. Then stop the storage thread. */
    public void shutdown() {
        try {
            io.execute(store::close);
        } catch (RejectedExecutionException e) {
            store.close();
        }
    }

    /** Whether the database is up (schema migrated, players loaded). */
    public boolean storageAvailable() {
        return storageUp;
    }

    /** Main thread: a player joined (or left); remembers them now and saves {@code name}/{@code last_seen} in the background. */
    public void seen(UUID uuid, String name) {
        remember(new PlayerRef(uuid, name), true);
        long now = clock.getAsLong();
        try {
            io.execute(() -> persist(uuid, name, now));
        } catch (RejectedExecutionException e) {
            log.fine("Storage is shut down; not saving " + name);
        }
    }

    private void persist(UUID uuid, String name, long now) {
        if (!storageUp) {
            return;
        }
        try {
            store.upsertSeen(uuid, name, now);
        } catch (StorageException e) {
            log.log(Level.WARNING, "Could not save player " + name, e);
        }
    }

    @Override
    public Optional<PlayerRef> cached(String name) {
        return name == null ? Optional.empty() : Optional.ofNullable(byName.get(key(name)));
    }

    @Override
    public Optional<PlayerRef> cached(UUID uuid) {
        return Optional.ofNullable(byId.get(uuid));
    }

    /** A read-only live view of every cached player (for tab completion: filter, do not copy). */
    public Collection<PlayerRef> cachedPlayers() {
        return Collections.unmodifiableCollection(byName.values());
    }

    @Override
    public CompletableFuture<Optional<PlayerRef>> lookup(String name) {
        Optional<PlayerRef> hit = cached(name);
        if (hit.isPresent() || name == null || name.isBlank() || !storageUp) {
            return CompletableFuture.completedFuture(hit);
        }
        CompletableFuture<Optional<PlayerRef>> result = new CompletableFuture<>();
        io.execute(() -> {
            try {
                Optional<PlayerRef> found = store.findByName(name.trim()).map(this::merge);
                main.execute(() -> result.complete(found));
            } catch (StorageException e) {
                main.execute(() -> result.completeExceptionally(e));
            }
        });
        return result;
    }

    /** Puts a database row into the cache unless a live join is fresher; returns the cached entry. */
    private synchronized PlayerRef merge(PlayerRecord record) {
        PlayerRef ref = new PlayerRef(record.uuid(), record.name());
        PlayerRef holder = byName.get(key(record.name()));
        if (live.contains(record.uuid()) || (holder != null && live.contains(holder.uuid()))) {
            return byId.getOrDefault(record.uuid(), ref);
        }
        return remember(ref, false);
    }

    private synchronized PlayerRef remember(PlayerRef ref, boolean fromJoin) {
        PlayerRef old = byId.put(ref.uuid(), ref);
        if (old != null) {
            byName.remove(key(old.name()), old); // renamed: the old name no longer points at them
        }
        byName.put(key(ref.name()), ref);
        if (fromJoin) {
            live.add(ref.uuid());
        }
        return ref;
    }

    private static String key(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
