package dev.casperrs.cookieCore.storage;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/** An active mute as the chat check needs it. Times are epoch milliseconds; {@code expiresAt} is null for a permanent mute. */
public record MuteRecord(long id, UUID targetUuid, String targetName, String actorName, String reason, long createdAt,
                         @Nullable Long expiresAt) {

    static MuteRecord of(BanRecord row) {
        return new MuteRecord(row.id(), row.targetUuid(), row.targetName(), row.actorName(), row.reason(), row.createdAt(), row.expiresAt());
    }

    public boolean expired(long now) {
        return expiresAt != null && expiresAt <= now;
    }
}
