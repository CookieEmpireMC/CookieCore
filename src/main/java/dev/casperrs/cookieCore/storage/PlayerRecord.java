package dev.casperrs.cookieCore.storage;

import java.util.UUID;

/** One row of {@code players}; times are epoch milliseconds. */
public record PlayerRecord(UUID uuid, String name, long firstSeen, long lastSeen) {
}
