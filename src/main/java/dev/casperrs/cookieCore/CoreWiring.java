package dev.casperrs.cookieCore;

import dev.casperrs.cookieCore.chat.ChatModule;
import dev.casperrs.cookieCore.command.CommandKit;
import dev.casperrs.cookieCore.command.CookieCoreCommand;
import dev.casperrs.cookieCore.command.ScoreboardCommand;
import dev.casperrs.cookieCore.config.PluginSettings;
import dev.casperrs.cookieCore.gamemode.FlyCommand;
import dev.casperrs.cookieCore.gamemode.FlyTracker;
import dev.casperrs.cookieCore.gamemode.GameModeCommand;
import dev.casperrs.cookieCore.gamemode.SpeedCommand;
import dev.casperrs.cookieCore.moderation.ModerationModule;
import dev.casperrs.cookieCore.player.PlayerDirectory;
import dev.casperrs.cookieCore.player.PlayerListener;
import dev.casperrs.cookieCore.scoreboard.ScoreboardModule;
import dev.casperrs.cookieCore.sound.SoundService;
import dev.casperrs.cookieCore.tab.TabModule;
import dev.casperrs.cookieCore.text.Messages;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Registers the listeners and the commands of every module, so {@link CookieCore} only starts and stops them. */
final class CoreWiring {

    private final JavaPlugin plugin;
    private final Messages messages;
    private final SoundService sounds;
    private final PlayerDirectory directory;
    private final Supplier<PluginSettings> settings;
    private final CookieCoreCommand.Reloader reloader;
    private final ScoreboardModule scoreboard;
    private final ModerationModule moderation;
    private final @Nullable ChatModule chat;
    private final TabModule tab;
    private final FlyTracker flyTracker;

    /** {@code main} runs a task on the main thread; {@code chat} is null when {@code chat.enabled} is false. */
    CoreWiring(JavaPlugin plugin, Messages messages, SoundService sounds, PlayerDirectory directory,
               Supplier<PluginSettings> settings, CookieCoreCommand.Reloader reloader, Consumer<Runnable> main,
               ScoreboardModule scoreboard, ModerationModule moderation, @Nullable ChatModule chat, TabModule tab) {
        this.plugin = plugin;
        this.messages = messages;
        this.sounds = sounds;
        this.directory = directory;
        this.settings = settings;
        this.reloader = reloader;
        this.scoreboard = scoreboard;
        this.moderation = moderation;
        this.chat = chat;
        this.tab = tab;
        this.flyTracker = new FlyTracker(main);
    }

    void registerListeners() {
        var plugins = plugin.getServer().getPluginManager();
        plugins.registerEvents(moderation.listener(), plugin);
        plugins.registerEvents(moderation.muteListener(), plugin);
        plugins.registerEvents(new PlayerListener(directory), plugin);
        Bukkit.getOnlinePlayers().forEach(p -> directory.seen(p.getUniqueId(), p.getName())); // after a /reload
        plugins.registerEvents(scoreboard.listener(), plugin);
        scoreboard.apply();
        plugins.registerEvents(tab.listener(), plugin);
        tab.apply();
        plugins.registerEvents(flyTracker, plugin);
        if (chat != null) {
            chat.listeners().forEach(listener -> plugins.registerEvents(listener, plugin));
            moderation.exemptFromChatMute(chat::staffChannelActive);
            chat.apply();
        }
    }

    void registerCommands() {
        CommandKit kit = new CommandKit(messages, sounds, directory);
        CookieCoreCommand command = new CookieCoreCommand(kit, messages, sounds, settings,
                plugin.getPluginMeta().getVersion(), directory::storageAvailable, reloader);
        ScoreboardCommand sidebarCommand = new ScoreboardCommand(kit, sounds, scoreboard.service());
        GameModeCommand gameMode = new GameModeCommand(kit, messages, sounds);
        FlyCommand fly = new FlyCommand(kit, messages, sounds, flyTracker);
        SpeedCommand speed = new SpeedCommand(kit, messages, sounds);
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register(command.node(), "CookieCore admin commands");
            event.registrar().register(sidebarCommand.node(), "Hide or show your sidebar", List.of("sb"));
            gameMode.register(event.registrar(), plugin.getLogger());
            event.registrar().register(fly.node(), "Toggle flight");
            event.registrar().register(speed.node(), "Change walking or flying speed");
            moderation.register(event.registrar(), kit);
            if (chat != null) {
                chat.register(event.registrar(), kit);
            }
        });
    }
}
