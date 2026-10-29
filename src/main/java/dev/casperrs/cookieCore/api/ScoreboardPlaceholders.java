package dev.casperrs.cookieCore.api;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.function.Function;

/**
 * Lets other plugins put their own values on the sidebar. A registered key becomes {@code <key>} in
 * {@code scoreboard.yml}. Call from the main thread, for example in {@code onEnable}.
 *
 * <p>The resolver runs on the main thread, about once per second for each player who sees the sidebar, and only while
 * {@code scoreboard.yml} uses the key: keep it fast and read from memory. A resolver that throws or returns
 * {@code null} shows empty text and is logged once. A plugin's placeholders are dropped when it is disabled.
 *
 * <p>The same keys are available in {@code tab.yml}. There the tab list's own {@code tps} and {@code rank_prefix} are built
 * in: a plugin may register those two names, but only the sidebar shows its value, the tab list keeps its own.
 */
public interface ScoreboardPlaceholders {

    /**
     * Registers {@code <key>}. The key must match {@code [a-z0-9_]+} and must not be a built-in ({@code player},
     * {@code server}, {@code online}, {@code max}, {@code ping}) or already registered.
     *
     * @throws IllegalArgumentException for a key that breaks those rules
     */
    void register(Plugin owner, String key, Function<Player, Component> resolver);

    /** Removes every placeholder registered by {@code owner}; takes effect on the next refresh. */
    void unregisterAll(Plugin owner);
}
