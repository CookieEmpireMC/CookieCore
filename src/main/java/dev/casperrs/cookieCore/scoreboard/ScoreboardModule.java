package dev.casperrs.cookieCore.scoreboard;

import dev.casperrs.cookieCore.api.ScoreboardPlaceholders;
import dev.casperrs.cookieCore.config.YamlFiles;
import dev.casperrs.cookieCore.log.WarnOnce;
import dev.casperrs.cookieCore.storage.SidebarStore;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Wires the sidebar feature together so the main class only has to start, reload and stop it. */
public final class ScoreboardModule {

    private final ScoreboardConfig config;
    private final PlaceholderRegistry registry;
    private final SidebarService service;
    private final Listener listener;

    /** {@code io} is the storage thread, {@code main} runs a task on the main thread; {@code enabled} reads {@code scoreboard.enabled}. */
    public ScoreboardModule(JavaPlugin plugin, SidebarStore store, Executor io, Executor main,
                            SidebarService.TaskRunner runner, BooleanSupplier enabled, Supplier<String> serverName) {
        WarnOnce warnOnce = new WarnOnce(plugin.getLogger());
        config = new ScoreboardConfig(() -> YamlFiles.load(plugin, "scoreboard.yml"), () -> YamlFiles.jarCopy(plugin, "scoreboard.yml"));
        registry = new PlaceholderRegistry(warnOnce);
        SidebarPreferences preferences = new SidebarPreferences(store, io, main, warnOnce);
        service = new SidebarService(runner, plugin.getLogger(), registry, config, preferences, enabled, serverName);
        listener = new SidebarListener(service, registry);
    }

    public ScoreboardPlaceholders placeholders() {
        return registry;
    }

    public SidebarService service() {
        return service;
    }

    public Listener listener() {
        return listener;
    }

    /** Reads {@code scoreboard.yml} (enable and reload); problems are added to {@code problems}. */
    public void loadConfig(List<String> problems) {
        config.load(problems);
    }

    /** After a reload: show or hide the sidebar for {@code scoreboard.enabled} and give it to the players online who should see it (also at enable, since a
     * {@code /reload} fires no join events). */
    public void apply() {
        service.reconfigure();
    }

    public void shutdown() {
        service.shutdown();
    }
}
