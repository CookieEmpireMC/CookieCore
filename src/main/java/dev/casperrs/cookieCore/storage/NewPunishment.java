package dev.casperrs.cookieCore.storage;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * A punishment to record. Times are epoch milliseconds; {@code actorUuid} is null for the console; {@code expiresAt}
 * is null for a permanent ban and for a kick.
 */
public record NewPunishment(PunishmentType type, UUID targetUuid, String targetName, @Nullable UUID actorUuid,
                            String actorName, String reason, long createdAt, @Nullable Long expiresAt, boolean silent) {
}
