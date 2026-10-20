package dev.casperrs.cookieCore.api;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Read-only lookup of players Core has seen before, online or not. Names are matched case-insensitively.
 * Never blocks: the cached methods read memory, {@link #lookup} goes to the database off the main thread.
 */
public interface PlayerLookup {

    /** The player with this name if Core has them in memory (every player seen since the last restart plus all stored ones). */
    Optional<PlayerRef> cached(String name);

    /** The player with this id, from memory. */
    Optional<PlayerRef> cached(UUID uuid);

    /**
     * Like {@link #cached(String)}, but asks the database when the name is not in memory (for example a player first
     * seen by another server on a shared database). The future completes on the server main thread.
     */
    CompletableFuture<Optional<PlayerRef>> lookup(String name);
}
