package dev.casperrs.cookieCore.storage;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence of the {@code players} table. Blocking and <b>single-threaded by contract</b>: every call runs on the
 * storage thread ({@link StorageThread}), never on the main thread. No Bukkit types in this package.
 */
public interface PlayerStore {

    /** Creates or migrates the schema. Call once, first. */
    void init() throws StorageException;

    /** Inserts the player or updates their name and {@code last_seen} ({@code first_seen} stays). */
    void upsertSeen(UUID uuid, String name, long now) throws StorageException;

    /** Every stored player, oldest {@code last_seen} first (so a later entry wins when two share a name). */
    List<PlayerRecord> loadAll() throws StorageException;

    /** The most recently seen player with this name (case-insensitive). */
    Optional<PlayerRecord> findByName(String name) throws StorageException;

    /** Closes the connection. */
    void close();
}
