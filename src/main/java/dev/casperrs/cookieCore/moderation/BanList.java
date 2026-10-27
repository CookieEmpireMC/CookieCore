package dev.casperrs.cookieCore.moderation;

import dev.casperrs.cookieCore.storage.BanRecord;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The active bans in memory, so the login check is a hash lookup and never touches the database. Filled from the
 * database once at start-up and kept in sync by {@link ModerationService}. Safe from any thread (the login check runs on
 * the authenticator threads). An expired tempban is ignored on read and left in the map.
 */
public final class BanList {

    private final ConcurrentHashMap<UUID, BanRecord> bans = new ConcurrentHashMap<>();
    private volatile boolean loaded;

    /** Whether the start-up load has finished; until then {@link #active} knows nothing and the login must wait. */
    public boolean loaded() {
        return loaded;
    }

    public void markLoaded() {
        loaded = true;
    }

    /** The start-up load: when the database holds two active bans for one player the longer lasting wins. */
    public void load(Collection<BanRecord> records) {
        records.forEach(ban -> bans.merge(ban.targetUuid(), ban, (old, fresh) -> outlasts(fresh, old) ? fresh : old));
    }

    /** A new ban replaces whatever is stored for the player (at most an expired one). */
    public void put(BanRecord ban) {
        bans.put(ban.targetUuid(), ban);
    }

    public void remove(UUID target) {
        bans.remove(target);
    }

    /** The ban that applies to {@code target} at {@code now}, empty when there is none or it has run out. */
    public Optional<BanRecord> active(UUID target, long now) {
        BanRecord ban = bans.get(target);
        return ban == null || ban.expired(now) ? Optional.empty() : Optional.of(ban);
    }

    /** Names of banned players that start with {@code lowerCasePrefix}, alphabetical, at most {@code limit}. */
    public List<String> activeNames(String lowerCasePrefix, int limit, long now) {
        return bans.values().stream()
                .filter(ban -> !ban.expired(now))
                .map(BanRecord::targetName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(lowerCasePrefix))
                .sorted(Comparator.comparing(name -> name.toLowerCase(Locale.ROOT)))
                .limit(limit)
                .toList();
    }

    private static boolean outlasts(BanRecord a, BanRecord b) {
        if (a.expiresAt() == null) {
            return b.expiresAt() != null;
        }
        return b.expiresAt() != null && a.expiresAt() > b.expiresAt();
    }
}
