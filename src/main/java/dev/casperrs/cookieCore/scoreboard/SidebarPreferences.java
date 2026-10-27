package dev.casperrs.cookieCore.scoreboard;

import dev.casperrs.cookieCore.log.WarnOnce;
import dev.casperrs.cookieCore.storage.SidebarStore;
import dev.casperrs.cookieCore.storage.StorageException;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

/**
 * Each online player's choice to see the sidebar or not. The choice is read from the database on the storage thread
 * when they join and answered on the main thread; a change is remembered at once and saved in the background. When the
 * database cannot be used the default (visible) applies and the choice lasts until the player leaves. Main thread only.
 */
final class SidebarPreferences {

    private final SidebarStore store;
    private final Executor io;
    private final Executor main;
    private final WarnOnce warnOnce;
    private final Map<UUID, Boolean> visible = new HashMap<>();

    SidebarPreferences(SidebarStore store, Executor io, Executor main, WarnOnce warnOnce) {
        this.store = store;
        this.io = io;
        this.main = main;
        this.warnOnce = warnOnce;
    }

    /** Calls {@code whenKnown} on the main thread with the player's choice, at once if it is already known. */
    void load(UUID id, Consumer<Boolean> whenKnown) {
        Boolean known = visible.get(id);
        if (known != null) {
            whenKnown.accept(known);
            return;
        }
        try {
            io.execute(() -> {
                boolean loaded = true;
                try {
                    loaded = store.loadVisible(id);
                } catch (StorageException | RuntimeException e) {
                    warnOnce.warn("load", "Could not load sidebar choices (showing the sidebar to everyone): " + e.getMessage());
                }
                boolean result = loaded;
                try {
                    main.execute(() -> whenKnown.accept(visible.computeIfAbsent(id, k -> result)));
                } catch (RuntimeException gone) {
                    // the plugin is disabling: nobody is waiting for the answer any more
                }
            });
        } catch (RejectedExecutionException shutDown) {
            // storage is closed because the plugin is disabling
        }
    }

    boolean isVisible(UUID id) {
        return visible.getOrDefault(id, true);
    }

    void set(UUID id, boolean value) {
        visible.put(id, value);
        try {
            io.execute(() -> {
                try {
                    store.saveVisible(id, value);
                } catch (StorageException | RuntimeException e) {
                    warnOnce.warn("save", "Could not save a sidebar choice (it lasts until the player leaves): " + e.getMessage());
                }
            });
        } catch (RejectedExecutionException shutDown) {
            // storage is closed because the plugin is disabling
        }
    }

    void forget(UUID id) {
        visible.remove(id);
    }
}
