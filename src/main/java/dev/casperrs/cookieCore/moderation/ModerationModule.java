package dev.casperrs.cookieCore.moderation;

import dev.casperrs.cookieCore.command.CommandKit;
import dev.casperrs.cookieCore.command.KnownPlayerArgument;
import dev.casperrs.cookieCore.player.PlayerDirectory;
import dev.casperrs.cookieCore.sound.SoundService;
import dev.casperrs.cookieCore.storage.BanRecord;
import dev.casperrs.cookieCore.storage.PunishmentStore;
import dev.casperrs.cookieCore.storage.StorageException;
import dev.casperrs.cookieCore.text.Messages;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Wires kick, ban, tempban and unban together so the main class only has to start and register them. */
public final class ModerationModule {

    private final Logger log;
    private final PunishmentStore store;
    private final Executor io;
    private final PlayerDirectory directory;
    private final Messages messages;
    private final LongSupplier clock;
    private final BanList bans = new BanList();
    private final ModerationService service;
    private final ModerationListener listener;
    /** False when the start-up load failed: bans then cannot be enforced or changed, same as with no database. */
    private volatile boolean usable;

    /** {@code io} is the storage thread, {@code main} runs a task on the main thread. */
    public ModerationModule(Logger log, PunishmentStore store, Executor io, Executor main, Messages messages,
                            SoundService sounds, PlayerDirectory directory, LongSupplier clock) {
        this.log = log;
        this.store = store;
        this.io = io;
        this.directory = directory;
        this.messages = messages;
        this.clock = clock;
        this.service = new ModerationService(store, io, main, bans, messages, sounds, log, clock, Bukkit::getOnlinePlayers,
                () -> usable && directory.storageAvailable());
        this.listener = new ModerationListener(bans, messages, clock);
    }

    public Listener listener() {
        return listener;
    }

    /**
     * Loads the active bans on the storage thread and opens the login. Call right after {@code PlayerDirectory.start()}: both
     * run in order on that one thread, so the schema is migrated first. Until it ran, every login gets the "starting" screen.
     */
    public void load() {
        io.execute(() -> {
            if (directory.storageAvailable()) {
                try {
                    List<BanRecord> active = store.loadActiveBans(clock.getAsLong());
                    bans.load(active);
                    usable = true;
                    log.info("Loaded " + active.size() + " active ban(s)");
                } catch (StorageException | RuntimeException e) {
                    log.log(Level.SEVERE, "Could not load the active bans: banned players can join until the next restart", e);
                }
            }
            bans.markLoaded();
        });
    }

    /** Registers the commands; a label another plugin owns is logged once and the rest keeps working. */
    public void register(Commands commands, CommandKit kit) {
        BannedPlayerArgument bannedPlayer = new BannedPlayerArgument(new KnownPlayerArgument(directory, messages), bans, clock);
        List<String> missing = new ArrayList<>();
        check(commands.register(new KickCommand(kit, service).node(), "Kick a player"), missing, "kick");
        check(commands.register(new BanCommand(kit, service).node(), "Ban a player"), missing, "ban");
        check(commands.register(new TempbanCommand(kit, service).node(), "Ban a player for a while"), missing, "tempban");
        check(commands.register(new UnbanCommand(kit, service, bannedPlayer).node(), "Unban a player", List.of("pardon")),
                missing, "unban", "pardon");
        if (!missing.isEmpty()) {
            log.warning("Could not take over /" + String.join(", /", missing)
                    + " (another plugin owns it); the namespaced cookiecore:<command> still works");
        }
    }

    private static void check(Set<String> registered, List<String> missing, String... wanted) {
        for (String label : wanted) {
            if (!registered.contains(label)) {
                missing.add(label);
            }
        }
    }
}
