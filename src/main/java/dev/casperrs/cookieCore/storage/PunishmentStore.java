package dev.casperrs.cookieCore.storage;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Persistence of the {@code punishments} table. Blocking and single-threaded by contract, on the storage thread only,
 * like {@link PlayerStore}. Rows are never deleted. No Bukkit types in this package.
 */
public interface PunishmentStore {

    /** Inserts the row and returns its generated id. */
    long insert(NewPunishment punishment) throws StorageException;

    /** Every ban that is active and has not expired at {@code now}. */
    List<BanRecord> loadActiveBans(long now) throws StorageException;

    /**
     * Marks every active ban of {@code target} inactive (an expired tempban stays active in the table, so a new ban after
     * it leaves two) and records who removed it, when and why. {@code byUuid} is null for the console. Returns how many
     * rows changed.
     */
    int deactivateBans(UUID target, @Nullable UUID byUuid, String byName, String reason, long now) throws StorageException;

    /** Every mute that is active and has not expired at {@code now}. */
    default List<MuteRecord> loadActiveMutes(long now) throws StorageException {
        return List.of();
    }

    /** Like {@link #deactivateBans} for mutes. */
    default int deactivateMutes(UUID target, @Nullable UUID byUuid, String byName, String reason, long now) throws StorageException {
        return 0;
    }
}
