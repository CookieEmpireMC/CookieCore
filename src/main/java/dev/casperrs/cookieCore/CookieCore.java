package dev.casperrs.cookieCore;

import dev.casperrs.cookieCore.api.CookieCoreApi;
import dev.casperrs.cookieCore.command.CommandKit;
import dev.casperrs.cookieCore.command.CookieCoreCommand;
import dev.casperrs.cookieCore.config.ConfigLoader;
import dev.casperrs.cookieCore.config.PluginSettings;
import dev.casperrs.cookieCore.player.PlayerDirectory;
import dev.casperrs.cookieCore.player.PlayerListener;
import dev.casperrs.cookieCore.sound.SoundService;
import dev.casperrs.cookieCore.storage.DriverConnectionFactory;
import dev.casperrs.cookieCore.storage.JdbcPlayerStore;
import dev.casperrs.cookieCore.storage.StorageThread;
import dev.casperrs.cookieCore.text.Messages;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Level;
import java.util.random.RandomGenerator;

/**
 * CookieCore: the base layer of the Cookie Empire server. This class only wires the services together and owns the
 * lifecycle; the features live in the sub-packages.
 *
 * <p><b>Enable order</b> (each step needs the one before it):
 * <ol>
 *   <li>settings, messages, sounds ({@link ConfigLoader}; a broken file falls back to the jar's copy)</li>
 *   <li>storage: the single IO thread and the JDBC store</li>
 *   <li>{@link PlayerDirectory}: its {@code start()} migrates the schema and warms the cache on the IO thread</li>
 *   <li>listeners</li>
 *   <li>commands (Brigadier lifecycle registration)</li>
 *   <li>{@link CookieCoreApi} into the services manager, last, so other plugins never see a half-started Core</li>
 * </ol>
 *
 * <p><b>Disable order</b> is the reverse; every step is wrapped so one throw never skips the next, and the storage
 * flush ({@link StorageThread#shutdown}) always runs last.
 */
public final class CookieCore extends JavaPlugin {

    private ConfigLoader loader;
    private PluginSettings settings;
    private Messages messages;
    private SoundService sounds;
    private ExecutorService io;
    private PlayerDirectory directory;
    private CoreService api;

    @Override
    public void onEnable() {
        // 1. settings, messages, sounds
        loader = new ConfigLoader(this);
        messages = new Messages(getLogger());
        // Core ships no resource pack yet, so nobody uses custom sounds: everybody hears the vanilla fallback
        sounds = new SoundService(getLogger(), RandomGenerator.getDefault(), player -> false);
        List<String> problems = new ArrayList<>();
        settings = loader.loadSettings(null, problems);
        loader.loadMessages(messages, problems);
        loader.loadSounds(sounds, problems);
        problems.forEach(getLogger()::warning);

        // 2. storage
        io = StorageThread.create("CookieCore-Storage");
        JdbcPlayerStore store = new JdbcPlayerStore(new DriverConnectionFactory(settings.database()),
                settings.database().tablePrefix(), getLogger());
        getLogger().info("Using " + settings.database().describe());

        // 3. player directory (migrates the schema, loads the known players; never blocks the main thread)
        directory = new PlayerDirectory(store, io, this::runOnMainThread, getLogger(), System::currentTimeMillis);
        directory.start();

        // 4. listeners
        getServer().getPluginManager().registerEvents(new PlayerListener(directory), this);
        Bukkit.getOnlinePlayers().forEach(p -> directory.seen(p.getUniqueId(), p.getName())); // after a /reload

        // 5. commands
        CookieCoreCommand command = new CookieCoreCommand(new CommandKit(messages, sounds, directory), messages, sounds,
                () -> settings, getPluginMeta().getVersion(), directory::storageAvailable, this::reload);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                event -> event.registrar().register(command.node(), "CookieCore admin commands"));

        // 6. public API
        api = new CoreService(getPluginMeta().getVersion(), directory);
        getServer().getServicesManager().register(CookieCoreApi.class, api, this, ServicePriority.Normal);

        getLogger().info("Enabled" + (problems.isEmpty() ? "" : " with " + problems.size() + " config problem(s), see above"));
    }

    @Override
    public void onDisable() {
        // a throw in any step must never skip the storage flush at the end
        safely("the public API", () -> {
            if (api != null) {
                getServer().getServicesManager().unregister(CookieCoreApi.class, api);
            }
        });
        safely("the player directory", () -> {
            if (directory != null) {
                // players still online: Paper may fire their quit events only after plugins are disabled, so refresh last_seen now
                Bukkit.getOnlinePlayers().forEach(p -> directory.seen(p.getUniqueId(), p.getName()));
                directory.shutdown(); // queues closing the database behind the pending writes
            }
        });
        safely("the storage thread", () -> {
            if (io != null) {
                StorageThread.shutdown(io, settings != null ? settings.storageTimeoutSeconds() : 10, getLogger());
            }
        });
    }

    /**
     * {@code /cookiecore reload}: config.yml (without {@code database.*}, which needs a restart), messages.yml and
     * sounds.yml. A file that cannot be read keeps its last good values. Returns the problems found, empty if all is well.
     */
    public List<String> reload() {
        List<String> problems = new ArrayList<>();
        PluginSettings fresh = loader.loadSettings(settings, problems);
        if (!fresh.database().equals(settings.database())) {
            problems.add("database.* changed; restart the server to switch databases");
        }
        settings = fresh.withDatabase(settings.database());
        loader.loadMessages(messages, problems);
        loader.loadSounds(sounds, problems);
        problems.forEach(getLogger()::warning);
        return problems;
    }

    private void runOnMainThread(Runnable task) {
        if (!isEnabled()) {
            // never drop the task silently: the caller (PlayerDirectory.lookup) fails its future instead of hanging
            throw new RejectedExecutionException("CookieCore is disabled");
        }
        Bukkit.getScheduler().runTask(this, task);
    }

    private void safely(String what, Runnable step) {
        try {
            step.run();
        } catch (RuntimeException e) {
            getLogger().log(Level.SEVERE, "Could not shut down " + what + " on disable", e);
        }
    }
}
