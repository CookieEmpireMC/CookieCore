package dev.casperrs.cookieCore.tab;

import dev.casperrs.cookieCore.config.YamlFiles;
import dev.casperrs.cookieCore.rank.Ranks;
import dev.casperrs.cookieCore.scoreboard.PlaceholderRegistry;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Wires the tab list together so the main class only has to start, reload and stop it. */
public final class TabModule {

    private final TabConfig config;
    private final TabService service;
    private final Listener listener;

    /** {@code enabled} reads {@code tab.enabled}, {@code serverName} reads {@code server.name}; {@code registry} holds the placeholders other plugins added. */
    public TabModule(JavaPlugin plugin, Ranks ranks, TabService.Repeater repeater, BooleanSupplier enabled,
                     Supplier<String> serverName, PlaceholderRegistry registry) {
        config = new TabConfig(() -> YamlFiles.load(plugin, "tab.yml"), () -> YamlFiles.jarCopy(plugin, "tab.yml"));
        service = new TabService(repeater, plugin.getLogger(), registry, config, new BukkitTabSink(), ranks.source(), enabled, serverName);
        listener = new TabListener(service, ranks);
    }

    public Listener listener() {
        return listener;
    }

    /** Reads {@code tab.yml} (enable and reload); problems are added to {@code problems}. */
    public void loadConfig(List<String> problems) {
        config.load(problems);
    }

    /** After enable and reload, once the ranks are read: apply {@code tab.enabled} and send the current texts to the players online. */
    public void apply() {
        service.reconfigure();
    }

    public void shutdown() {
        service.shutdown();
    }
}
