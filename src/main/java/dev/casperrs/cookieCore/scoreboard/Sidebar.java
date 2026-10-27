package dev.casperrs.cookieCore.scoreboard;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One player's sidebar: their own {@link Scoreboard} (never the main one), kept by a strong reference until the player
 * leaves. Main thread only.
 */
final class Sidebar {

    /** Values that are the same for every player in one refresh. */
    record ServerValues(String serverName, int online, int max) {
    }

    private final Player player;
    private final Scoreboard board;
    private final SidebarSink sink;
    private final SidebarDiff diff = new SidebarDiff();
    private @Nullable CompiledLayout lastLayout;
    private Object @Nullable [] lastValues;

    private Sidebar(Player player, Scoreboard board, SidebarSink sink) {
        this.player = player;
        this.board = board;
        this.sink = sink;
    }

    /** Builds the board, fills it completely, then gives it to the player, so they never see a half-filled board. */
    static Sidebar open(Player player, CompiledLayout layout, ServerValues server, PlaceholderRegistry registry) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = board.registerNewObjective("sidebar", Criteria.DUMMY, Component.empty());
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.numberFormat(NumberFormat.blank());
        Sidebar sidebar = new Sidebar(player, board, new BoardSink(objective));
        sidebar.refresh(layout, server, registry);
        player.setScoreboard(board);
        return sidebar;
    }

    Player player() {
        return player;
    }

    /** False when another plugin gave the player a different scoreboard. */
    boolean ownsBoard() {
        return player.getScoreboard() == board;
    }

    /** Hands the player back to the main scoreboard, unless someone else already took them. */
    void close() {
        if (ownsBoard()) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    void refresh(CompiledLayout layout, ServerValues server, PlaceholderRegistry registry) {
        List<String> keys = layout.keys();
        Object[] values = new Object[keys.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = value(keys.get(i), server, registry);
        }
        // the cheap exit: same layout and same values as last time means the same Components, so skip rendering them
        if (layout == lastLayout && Arrays.equals(values, lastValues)) {
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
        List<Component> lines = new ArrayList<>(layout.lines().size());
        layout.lines().forEach(line -> lines.add(line.render(text, components)));
        diff.apply(sink, layout.title().render(text, components), lines);
        lastLayout = layout;
        lastValues = values;
    }

    private Object value(String key, ServerValues server, PlaceholderRegistry registry) {
        return switch (key) {
            case BuiltInPlaceholders.PLAYER -> player.getName();
            case BuiltInPlaceholders.SERVER -> server.serverName();
            case BuiltInPlaceholders.ONLINE -> String.valueOf(server.online());
            case BuiltInPlaceholders.MAX -> String.valueOf(server.max());
            case BuiltInPlaceholders.PING -> String.valueOf(player.getPing());
            default -> registry.resolve(key, player);
        };
    }
}
