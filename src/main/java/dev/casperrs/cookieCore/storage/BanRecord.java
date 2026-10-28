package dev.casperrs.cookieCore.storage;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/** An active ban as the login check needs it. Times are epoch milliseconds; {@code expiresAt} is null for a permanent ban. */
public record BanRecord(long id, UUID targetUuid, String targetName, String actorName, String reason, long createdAt,
                        @Nullable Long expiresAt) {

    public boolean expired(long now) {
        return expiresAt != null && expiresAt <= now;
    }
}
