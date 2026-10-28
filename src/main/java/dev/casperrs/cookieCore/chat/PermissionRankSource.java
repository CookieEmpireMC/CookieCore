package dev.casperrs.cookieCore.chat;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * The prefix from the ranks in {@code chat.yml}: the first permission the player has. Bukkit's permission data may not be read
 * safely from the chat thread, so the answer is worked out on the main thread (join and reload) and the chat thread only reads
 * the cache. A permission that changes mid-session shows after the next join or {@code /cookiecore reload}.
 */
public final class PermissionRankSource implements RankSource {

    private final Supplier<@Nullable ChatSettings> settings;
    private volatile Map<UUID, Optional<Component>> cache = new ConcurrentHashMap<>();

    public PermissionRankSource(Supplier<@Nullable ChatSettings> settings) {
        this.settings = settings;
    }

    /** Main thread. */
    public void refresh(Player player) {
        cache.put(player.getUniqueId(), resolve(player));
    }

    /** Main thread. The new cache is built aside and swapped in, so the chat thread never sees it half filled. */
    public void refreshAll(Collection<? extends Player> online) {
        Map<UUID, Optional<Component>> fresh = new ConcurrentHashMap<>();
        online.forEach(player -> fresh.put(player.getUniqueId(), resolve(player)));
        cache = fresh;
    }

    public void forget(UUID player) {
        cache.remove(player);
    }

    @Override
    public Optional<Component> prefix(Player player) {
        Optional<Component> known = cache.get(player.getUniqueId());
        if (known != null) {
            return known;
        }
        // not resolved on the main thread yet: Bukkit permissions must not be read from the chat thread
        ChatSettings current = settings.get();
        return current == null ? Optional.empty() : current.prefixFor(permission -> false);
    }

    private Optional<Component> resolve(Player player) {
        ChatSettings current = settings.get();
        return current == null ? Optional.empty() : current.prefixFor(player::hasPermission);
    }
}
