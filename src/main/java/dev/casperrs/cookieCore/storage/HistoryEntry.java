package dev.casperrs.cookieCore.storage;

import org.jspecify.annotations.Nullable;

/** One row of a player's punishment history. Times are epoch milliseconds; {@code expiresAt} is null for a permanent or one-off row. */
public record HistoryEntry(long id, PunishmentType type, String actorName, String reason, long createdAt,
                           @Nullable Long expiresAt, boolean active) {
}
