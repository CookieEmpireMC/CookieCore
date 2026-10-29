package dev.casperrs.cookieCore.scoreboard;

import dev.casperrs.cookieCore.api.ScoreboardPlaceholders;
import dev.casperrs.cookieCore.log.WarnOnce;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * The placeholders other plugins registered. Registration may come from any plugin's {@code onEnable}; resolving runs
 * on the main thread. {@link #version} changes whenever the key set changes, so templates know when to be compiled again.
 */
public final class PlaceholderRegistry implements ScoreboardPlaceholders {

    private static final Pattern KEY = Pattern.compile("[a-z0-9_]+");

    private record Entry(Plugin owner, Function<Player, Component> resolver) {
    }

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final AtomicInteger version = new AtomicInteger();
    private final WarnOnce warnOnce;

    public PlaceholderRegistry(WarnOnce warnOnce) {
        this.warnOnce = warnOnce;
    }

    @Override
    public void register(Plugin owner, String key, Function<Player, Component> resolver) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(resolver, "resolver");
        if (!KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("Scoreboard placeholder key '" + key + "' must match [a-z0-9_]+");
        }
        if (BuiltInPlaceholders.ALL.contains(key)) {
            throw new IllegalArgumentException("Scoreboard placeholder key '" + key + "' is built in and cannot be registered");
        }
        Entry previous = entries.putIfAbsent(key, new Entry(owner, resolver));
        if (previous != null) {
            throw new IllegalArgumentException("Scoreboard placeholder '" + key + "' is already registered by "
                    + previous.owner().getName());
        }
        version.incrementAndGet();
    }

    @Override
    public void unregisterAll(Plugin owner) {
        if (entries.values().removeIf(entry -> entry.owner() == owner)) {
            version.incrementAndGet();
        }
    }

    /** Changes whenever a key is added or removed. */
    public int version() {
        return version.get();
    }

    public List<String> keys() {
        return entries.keySet().stream().sorted().toList();
    }

    /** The value for {@code key}; empty text when the key is gone or its resolver throws or returns null (logged once per key). */
    public Component resolve(String key, Player player) {
        Entry entry = entries.get(key);
        if (entry == null) {
            return Component.empty();
        }
        try {
            Component value = entry.resolver().apply(player);
            if (value != null) {
                return value;
            }
            warnOnce.warn(key, "Scoreboard placeholder '" + key + "' from " + entry.owner().getName() + " returned null; showing nothing");
        } catch (RuntimeException e) {
            warnOnce.warn(key, "Scoreboard placeholder '" + key + "' from " + entry.owner().getName() + " threw " + e + "; showing nothing");
        }
        return Component.empty();
    }
}
