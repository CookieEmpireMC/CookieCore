package dev.casperrs.cookieCore.chat;

import org.jspecify.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Who each player last exchanged a private message with, for {@code /r}. Memory only; a player's own entry goes when they quit. */
public final class ReplyTracker {

    private final ConcurrentHashMap<UUID, UUID> partners = new ConcurrentHashMap<>();

    /** Both sides can now answer each other with {@code /r}. */
    public void record(UUID sender, UUID target) {
        partners.put(sender, target);
        partners.put(target, sender);
    }

    /** Null when the player has not talked to anyone yet. The partner may have left since; that is for the caller to find out. */
    public @Nullable UUID partner(UUID player) {
        return partners.get(player);
    }

    public void forget(UUID player) {
        partners.remove(player);
    }
}
