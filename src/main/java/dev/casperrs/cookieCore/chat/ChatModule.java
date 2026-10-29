package dev.casperrs.cookieCore.chat;

import dev.casperrs.cookieCore.Perm;
import dev.casperrs.cookieCore.command.CommandKit;
import dev.casperrs.cookieCore.config.YamlFiles;
import dev.casperrs.cookieCore.moderation.MuteList;
import dev.casperrs.cookieCore.rank.RankSource;
import dev.casperrs.cookieCore.rank.Ranks;
import dev.casperrs.cookieCore.sound.SoundEvents;
import dev.casperrs.cookieCore.sound.SoundService;
import dev.casperrs.cookieCore.text.Messages;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.audience.Audience;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.logging.Logger;

/** Wires chat format, join and quit lines, MOTD, private messages, staff chat and the mute-expiry notice together. */
public final class ChatModule {

    private final Logger log;
    private final Messages messages;
    private final SoundService sounds;
    private final MuteList mutes;
    private final LongSupplier clock;
    private final ChatConfig config;
    private final ReplyTracker replies = new ReplyTracker();
    private final StaffChat staff;
    private final MuteExpiryNotifier expiry;
    private final List<Listener> listeners;

    /** {@code main} runs a task on the main thread; {@code serverName} reads {@code server.name} of config.yml; {@code rankSet} is shared with the tab list. */
    public ChatModule(JavaPlugin plugin, Messages messages, SoundService sounds, MuteList mutes, LongSupplier clock,
                      Executor main, MuteExpiryNotifier.Repeater repeater, Supplier<String> serverName, Ranks rankSet) {
        this.log = plugin.getLogger();
        this.messages = messages;
        this.sounds = sounds;
        this.mutes = mutes;
        this.clock = clock;
        this.config = new ChatConfig(() -> YamlFiles.load(plugin, "chat.yml"), () -> YamlFiles.jarCopy(plugin, "chat.yml"));
        RankSource ranks = rankSet.source();
        this.staff = new StaffChat(config, messages, ranks, serverName, ChatModule::staffReaders);
        this.expiry = new MuteExpiryNotifier(mutes, clock, Bukkit::getOnlinePlayers, player -> {
            messages.send(player, "moderation.mute-expired");
            sounds.play(player, SoundEvents.UI_NOTIFY);
        }, repeater);
        this.listeners = List.of(new ChatListener(config, ranks, staff, main, serverName),
                new JoinListener(messages, config, serverName, rankSet.permission(), replies, staff, expiry),
                new MotdListener(config, messages, serverName));
    }

    public List<Listener> listeners() {
        return listeners;
    }

    /** Reads {@code chat.yml} (enable and reload); problems are added to {@code problems}. */
    public void loadConfig(List<String> problems) {
        config.load(problems);
    }

    /** After enable and reload: restart the expiry notices for the players already online (a {@code /reload} fires no join events). */
    public void apply() {
        if (!Bukkit.getOnlinePlayers().isEmpty()) {
            expiry.start();
        }
    }

    /** True while the player's chat goes to the staff channel and they may still use it; MuteListener lets that chat through. Chat thread. */
    public boolean staffChannelActive(Player player) {
        return staff.active(player.getUniqueId()) && player.hasPermission(Perm.STAFF_CHAT);
    }

    /** Registers the commands; a label another plugin owns is logged once and the rest keeps working. */
    public void register(Commands commands, CommandKit kit) {
        MessageCommand message = new MessageCommand(kit, messages, sounds, replies, mutes, clock, Bukkit::getPlayer);
        List<String> missing = new ArrayList<>();
        CommandKit.collectMissing(commands.register(message.messageNode(), "Send a private message",
                List.of("tell", "w", "whisper", "pm")), missing, "msg", "tell", "w", "whisper", "pm");
        CommandKit.collectMissing(commands.register(message.replyNode(), "Reply to your last private message", List.of("reply")),
                missing, "r", "reply");
        CommandKit.collectMissing(commands.register(new StaffChatCommand(kit, staff).node(), "Talk in the staff channel",
                List.of("staffchat")), missing, "sc", "staffchat");
        if (!missing.isEmpty()) {
            log.warning("Could not take over /" + String.join(", /", missing)
                    + " (another plugin owns it); the namespaced cookiecore:<command> still works");
        }
    }

    public void shutdown() {
        expiry.stop();
    }

    private static List<Audience> staffReaders() {
        return staffReaders(Bukkit.getOnlinePlayers(), Bukkit.getConsoleSender());
    }

    /** The online players with the staff chat permission, then the console. */
    static List<Audience> staffReaders(Collection<? extends Player> online, Audience console) {
        List<Audience> readers = new ArrayList<>();
        for (Player player : online) {
            if (player.hasPermission(Perm.STAFF_CHAT)) {
                readers.add(player);
            }
        }
        readers.add(console);
        return readers;
    }
}
