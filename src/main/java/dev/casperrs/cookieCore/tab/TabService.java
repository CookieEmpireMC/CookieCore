package dev.casperrs.cookieCore.tab;

import dev.casperrs.cookieCore.rank.RankSource;
import dev.casperrs.cookieCore.scoreboard.PlaceholderRegistry;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Header, footer, name and position of everybody in the tab list. One repeating task refreshes all viewers once a second;
 * it starts with the first viewer and stops with the last, so an empty server runs nothing. Per viewer the service
 * remembers what it sent and sends a packet only when the rendered text changed: Paper does not skip repeated values. It
 * never reads back what another plugin set, so it cannot start a tug of war; a reload sends everything once more.
 * Main thread only.
 */
public final class TabService {

    /** Starts a repeating main-thread task (period in ticks) and returns the function that cancels it. */
    @FunctionalInterface
    public interface Repeater {
        Runnable repeat(Runnable task, long periodTicks);
    }

    static final long PERIOD_TICKS = 20L;
    private static final double MAX_TPS = 20.0;

    /** What one viewer has been sent; the rank part is fixed at join and reload, so a rank change shows at the next one. */
    private static final class Viewer {
        final Player player;
        final Component prefix;
        final int order;
        @Nullable CompiledTab lastLayout;
        Object @Nullable [] lastValues;
        Component sentHeader = Component.empty();
        Component sentFooter = Component.empty();
        @Nullable Component sentName;

        Viewer(Player player, Component prefix, int order) {
            this.player = player;
            this.prefix = prefix;
            this.order = order;
        }
    }

    private record ServerValues(String name, int online, int max, String tps) {
    }

    private final Repeater repeater;
    private final Logger log;
    private final PlaceholderRegistry registry;
    private final TabConfig config;
    private final TabSink sink;
    private final RankSource ranks;
    private final BooleanSupplier enabled;
    private final Supplier<String> serverName;

    private final Map<UUID, Viewer> viewers = new HashMap<>();
    private @Nullable Runnable cancelTask;
    private @Nullable CompiledTab compiled;

    TabService(Repeater repeater, Logger log, PlaceholderRegistry registry, TabConfig config, TabSink sink, RankSource ranks,
               BooleanSupplier enabled, Supplier<String> serverName) {
        this.repeater = repeater;
        this.log = log;
        this.registry = registry;
        this.config = config;
        this.sink = sink;
        this.ranks = ranks;
        this.enabled = enabled;
        this.serverName = serverName;
    }

    /** A player joined (or Core was reloaded with them online): send their name, position, header and footer now. */
    public void join(Player player) {
        CompiledTab layout = layout();
        if (!enabled.getAsBoolean() || layout == null || !player.isOnline()) {
            return;
        }
        Viewer viewer = new Viewer(player, ranks.prefix(player).orElse(Component.empty()), Math.max(0, ranks.order(player)));
        viewers.put(player.getUniqueId(), viewer);
        try {
            if (sink.order(player) != viewer.order) {
                sink.order(player, viewer.order);
            }
            refresh(viewer, layout, serverValues());
        } catch (RuntimeException e) {
            drop(viewer, e);
        }
        if (cancelTask == null && !viewers.isEmpty()) {
            cancelTask = repeater.repeat(this::tick, PERIOD_TICKS);
        }
    }

    /** The player left: forget what was sent to them. */
    public void quit(Player player) {
        viewers.remove(player.getUniqueId());
        stopTaskIfIdle();
    }

    /**
     * After a reload and at enable: apply {@code tab.enabled} and send everything once more to the players online (a
     * {@code /reload} fires no join events). Switched off, the tab list goes back to the vanilla look.
     */
    public void reconfigure() {
        if (!enabled.getAsBoolean()) {
            resetAll();
            return;
        }
        viewers.clear(); // forget what was sent, so the join below sends it all once more
        stopTaskIfIdle();
        sink.onlinePlayers().forEach(this::join);
    }

    /** The plugin is disabling: give everybody the vanilla tab list back and stop the task. */
    public void shutdown() {
        resetAll();
    }

    private void resetAll() {
        for (Viewer viewer : List.copyOf(viewers.values())) {
            try {
                sink.listName(viewer.player, null);
                sink.order(viewer.player, 0);
                sink.headerAndFooter(viewer.player, Component.empty(), Component.empty());
            } catch (RuntimeException e) {
                log.log(Level.FINE, "Could not reset the tab list of " + viewer.player.getName(), e);
            }
        }
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
        CompiledTab layout = layout();
        if (layout == null) {
            return;
        }
        ServerValues server = serverValues();
        for (Viewer viewer : List.copyOf(viewers.values())) {
            if (!viewer.player.isOnline()) {
                viewers.remove(viewer.player.getUniqueId());
                continue;
            }
            try {
                refresh(viewer, layout, server);
            } catch (RuntimeException e) {
                drop(viewer, e);
            }
        }
        stopTaskIfIdle();
    }

    private void drop(Viewer viewer, RuntimeException e) {
        log.log(Level.WARNING, "Could not update the tab list of " + viewer.player.getName() + "; leaving it alone until they rejoin", e);
        viewers.remove(viewer.player.getUniqueId());
    }

    private void refresh(Viewer viewer, CompiledTab layout, ServerValues server) {
        List<String> keys = layout.keys();
        Object[] values = new Object[keys.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = value(keys.get(i), viewer, server);
        }
        // the cheap exit: same layout and same values as last time means the same texts, so skip rendering them
        if (layout == viewer.lastLayout && Arrays.equals(values, viewer.lastValues)) {
            return;
        }
        Map<String, String> text = new HashMap<>();
        Map<String, Component> components = new HashMap<>();
        for (int i = 0; i < values.length; i++) {
            if (values[i] instanceof Component c) {
                components.put(keys.get(i), c);
            } else {
                text.put(keys.get(i), (String) values[i]);
            }
        }
        viewer.lastLayout = layout;
        viewer.lastValues = values;
        Component header = layout.renderHeader(text, components);
        Component footer = layout.renderFooter(text, components);
        if (!header.equals(viewer.sentHeader) || !footer.equals(viewer.sentFooter)) {
            sink.headerAndFooter(viewer.player, header, footer);
            viewer.sentHeader = header;
            viewer.sentFooter = footer;
        }
        Component name = layout.renderName(text, components);
        if (!name.equals(viewer.sentName)) {
            sink.listName(viewer.player, name);
            viewer.sentName = name;
        }
    }

    private Object value(String key, Viewer viewer, ServerValues server) {
        return switch (key) {
            case CompiledTab.PLAYER -> viewer.player.getName();
            case CompiledTab.SERVER -> server.name();
            case CompiledTab.ONLINE -> String.valueOf(server.online());
            case CompiledTab.MAX -> String.valueOf(server.max());
            case CompiledTab.PING -> String.valueOf(sink.ping(viewer.player));
            case CompiledTab.TPS -> server.tps();
            case CompiledTab.RANK_PREFIX -> viewer.prefix;
            default -> registry.resolve(key, viewer.player);
        };
    }

    private @Nullable CompiledTab layout() {
        TabSettings source = config.current();
        if (source == null) {
            return null;
        }
        int version = registry.version();
        CompiledTab current = compiled;
        if (current == null || current.source() != source || current.registryVersion() != version) {
            List<String> known = new ArrayList<>(CompiledTab.BUILT_IN);
            registry.keys().stream().filter(key -> !CompiledTab.BUILT_IN.contains(key)).forEach(known::add);
            current = CompiledTab.compile(source, version, known);
            compiled = current;
        }
        return current;
    }

    private ServerValues serverValues() {
        return new ServerValues(serverName.get(), sink.online(), sink.maxPlayers(), formatTps(sink.tps()));
    }

    /** One decimal, at most 20.0 (a server catching up reports a little above), so a jitter inside the shown digit sends nothing. */
    static String formatTps(double tps) {
        double shown = Double.isNaN(tps) ? 0.0 : Math.max(0.0, Math.min(MAX_TPS, tps));
        return String.format(Locale.ROOT, "%.1f", shown);
    }
}
