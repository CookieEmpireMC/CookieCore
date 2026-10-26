package dev.casperrs.cookieCore.scoreboard;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Shows the sidebar to the players who want it. One repeating task refreshes everybody once a second; it starts with the
 * first viewer and stops with the last, so an empty server runs nothing. Main thread only.
 *
 * <p>If another plugin gives a player a different scoreboard, that player is left alone until they rejoin: fighting
 * over the screen every second would be worse than a missing sidebar.
 */
public final class SidebarService {

    /** Starts a repeating main-thread task (period in ticks) and returns the function that cancels it. */
    @FunctionalInterface
    public interface TaskRunner {
        Runnable repeat(Runnable task, long periodTicks);
    }

    public enum ToggleResult { DISABLED, SHOWN, HIDDEN }

    private static final long PERIOD_TICKS = 20L;

    private final TaskRunner runner;
    private final Logger log;
    private final PlaceholderRegistry registry;
    private final ScoreboardConfig config;
    private final SidebarPreferences preferences;
    private final BooleanSupplier enabled;
    private final Supplier<String> serverName;

    private final Map<UUID, Sidebar> viewers = new HashMap<>();
    private final Set<UUID> replacedByOthers = new HashSet<>();
    private @Nullable Runnable cancelTask;
    private @Nullable CompiledLayout compiled;

    SidebarService(TaskRunner runner, Logger log, PlaceholderRegistry registry, ScoreboardConfig config,
                   SidebarPreferences preferences, BooleanSupplier enabled, Supplier<String> serverName) {
        this.runner = runner;
        this.log = log;
        this.registry = registry;
        this.config = config;
        this.preferences = preferences;
        this.enabled = enabled;
        this.serverName = serverName;
    }

    /** A player joined (or Core was reloaded with them online): show the sidebar once their saved choice is known. */
    public void join(Player player) {
        if (!enabled.getAsBoolean()) {
            return;
        }
        UUID id = player.getUniqueId();
        preferences.load(id, wanted -> {
            if (!player.isOnline()) {
                preferences.forget(id);
            } else if (wanted && enabled.getAsBoolean() && !replacedByOthers.contains(id)) {
                show(player);
            }
        });
    }

    /** The player left. Their scoreboard must not be touched any more: the server has already cleared it. */
    public void quit(Player player) {
        UUID id = player.getUniqueId();
        viewers.remove(id);
        replacedByOthers.remove(id);
        preferences.forget(id);
        stopTaskIfIdle();
    }

    public ToggleResult toggle(Player player) {
        if (!enabled.getAsBoolean()) {
            return ToggleResult.DISABLED;
        }
        UUID id = player.getUniqueId();
        boolean nowVisible = !preferences.isVisible(id);
        preferences.set(id, nowVisible);
        if (nowVisible) {
            replacedByOthers.remove(id);
            show(player);
            return ToggleResult.SHOWN;
        }
        hide(id);
        return ToggleResult.HIDDEN;
    }

    /** After a reload: apply {@code scoreboard.enabled} and let the next refresh pick up a changed layout. */
    public void reconfigure() {
        if (!enabled.getAsBoolean()) {
            hideAll();
            return;
        }
        Bukkit.getOnlinePlayers().forEach(this::join);
    }

    /** The plugin is disabling: give everybody the main scoreboard back and stop the task. */
    public void shutdown() {
        hideAll();
    }

    private void show(Player player) {
        UUID id = player.getUniqueId();
        CompiledLayout layout = layout();
        if (layout == null || viewers.containsKey(id) || !player.isOnline()) {
            return;
        }
        viewers.put(id, Sidebar.open(player, layout, serverValues(), registry));
        if (cancelTask == null) {
            cancelTask = runner.repeat(this::tick, PERIOD_TICKS);
        }
    }

    private void hide(UUID id) {
        Sidebar sidebar = viewers.remove(id);
        if (sidebar != null) {
            sidebar.close();
        }
        stopTaskIfIdle();
    }

    private void hideAll() {
        viewers.values().forEach(Sidebar::close);
        viewers.clear();
        stopTaskIfIdle();
    }

    private void stopTaskIfIdle() {
        if (viewers.isEmpty() && cancelTask != null) {
            cancelTask.run();
            cancelTask = null;
        }
    }

    private void tick() {
        CompiledLayout layout = layout();
        if (layout == null) {
            hideAll();
            return;
        }
        Sidebar.ServerValues server = serverValues();
        viewers.entrySet().removeIf(entry -> !refresh(entry.getKey(), entry.getValue(), layout, server));
        stopTaskIfIdle();
    }

    private boolean refresh(UUID id, Sidebar sidebar, CompiledLayout layout, Sidebar.ServerValues server) {
        Player player = sidebar.player();
        if (!player.isOnline()) {
            return false;
        }
        if (!sidebar.ownsBoard()) {
            replacedByOthers.add(id);
            log.info("Another plugin gave " + player.getName() + " a different scoreboard; the sidebar leaves them alone until they rejoin");
            return false;
        }
        try {
            sidebar.refresh(layout, server, registry);
            return true;
        } catch (RuntimeException e) {
            log.log(Level.WARNING, "Could not update the sidebar of " + player.getName() + "; hiding it for them", e);
            replacedByOthers.add(id);
            return false;
        }
    }

    private @Nullable CompiledLayout layout() {
        ScoreboardLayout source = config.current();
        if (source == null) {
            return null;
        }
        int version = registry.version();
        CompiledLayout current = compiled;
        if (current == null || current.source() != source || current.registryVersion() != version) {
            List<String> known = new ArrayList<>(BuiltInPlaceholders.ALL);
            known.addAll(registry.keys());
            current = CompiledLayout.compile(source, version, known);
            compiled = current;
        }
        return current;
    }

    private Sidebar.ServerValues serverValues() {
        return new Sidebar.ServerValues(serverName.get(), Bukkit.getOnlinePlayers().size(), Bukkit.getMaxPlayers());
    }
}
