package dev.casperrs.cookieCore.moderation;

import dev.casperrs.cookieCore.storage.MuteRecord;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The active mutes in memory, so the chat check is a hash lookup and never touches the database. Safe from any thread
 * (chat runs async). An expired tempmute is ignored on read and left in the map.
 */
public final class MuteList {

    private final ConcurrentHashMap<UUID, MuteRecord> mutes = new ConcurrentHashMap<>();
    private volatile boolean loaded;
    private volatile boolean loadFailed;

    /** Whether the start-up load has finished; until then chat is not blocked, since a mute is no security boundary. */
    public boolean loaded() {
        return loaded;
    }

    public void markLoaded() {
        loaded = true;
    }

    /**
     * True when the start-up load failed. The list is then incomplete and never marked loaded, so the chat is not blocked and
     * the mute commands refuse to change it; a restart loads the mutes again.
     */
    public boolean loadFailed() {
        return loadFailed;
    }

    public void markLoadFailed() {
        loadFailed = true;
    }

    /** The start-up load: when the database holds two active mutes for one player the longer lasting wins. */
    public void load(Collection<MuteRecord> records) {
        records.forEach(mute -> mutes.merge(mute.targetUuid(), mute, (old, fresh) -> outlasts(fresh, old) ? fresh : old));
    }

    public void put(MuteRecord mute) {
        mutes.put(mute.targetUuid(), mute);
    }

    public void remove(UUID target) {
        mutes.remove(target);
    }

    public Optional<MuteRecord> active(UUID target, long now) {
        MuteRecord mute = mutes.get(target);
        return mute == null || mute.expired(now) ? Optional.empty() : Optional.of(mute);
    }

    /** Names of muted players that start with {@code lowerCasePrefix}, alphabetical, at most {@code limit}. */
    public List<String> activeNames(String lowerCasePrefix, int limit, long now) {
        return mutes.values().stream()
                .filter(mute -> !mute.expired(now))
                .map(MuteRecord::targetName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(lowerCasePrefix))
                .sorted(Comparator.comparing(name -> name.toLowerCase(Locale.ROOT)))
                .limit(limit)
                .toList();
    }

    private static boolean outlasts(MuteRecord a, MuteRecord b) {
        if (a.expiresAt() == null) {
            return b.expiresAt() != null;
        }
        return b.expiresAt() != null && a.expiresAt() > b.expiresAt();
    }
}
