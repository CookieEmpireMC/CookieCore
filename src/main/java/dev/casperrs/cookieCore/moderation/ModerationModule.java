package dev.casperrs.cookieCore.moderation;

import dev.casperrs.cookieCore.command.CommandKit;
import dev.casperrs.cookieCore.command.KnownPlayerArgument;
import dev.casperrs.cookieCore.player.PlayerDirectory;
import dev.casperrs.cookieCore.sound.SoundService;
import dev.casperrs.cookieCore.storage.BanRecord;
import dev.casperrs.cookieCore.log.WarnOnce;
import dev.casperrs.cookieCore.storage.MuteRecord;
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
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Wires kick, ban, tempban, unban and the mute commands together so the main class only has to start and register them. */
public final class ModerationModule {

    private final Logger log;
    private final PunishmentStore store;
    private final Executor io;
    private final PlayerDirectory directory;
    private final Messages messages;
    private final LongSupplier clock;
    private final BanList bans = new BanList();
    private final MuteList mutes = new MuteList();
    private final ModerationService service;
    private final PunishmentHistory history;
    private final ModerationListener listener;
    private final MuteListener muteListener;
    /** False until the start-up loads have finished (bans loaded, mute load run): nothing can be changed meanwhile, same as with no database. */
    private volatile boolean usable;
    private final AtomicBoolean loading = new AtomicBoolean();
    /** Storage thread only. */
    private boolean loadFailureLogged;

    /** {@code io} is the storage thread, {@code main} runs a task on the main thread. */
    public ModerationModule(Logger log, PunishmentStore store, Executor io, Executor main, Messages messages,
                            SoundService sounds, PlayerDirectory directory, LongSupplier clock) {
        this.log = log;
        this.store = store;
        this.io = io;
        this.directory = directory;
        this.messages = messages;
        this.clock = clock;
        this.service = new ModerationService(store, io, main, bans, mutes, messages, sounds, log, clock, Bukkit::getOnlinePlayers,
                () -> usable && directory.storageAvailable());
        this.history = new PunishmentHistory(store, io, main, messages, sounds, log, clock, () -> directory.storageAvailable());
        this.listener = new ModerationListener(bans, messages, clock, this::load);
        this.muteListener = new MuteListener(mutes, messages, clock, new WarnOnce(log));
    }

    public Listener listener() {
        return listener;
    }

    public Listener muteListener() {
        return muteListener;
    }

    /**
     * Loads the active bans on the storage thread and opens the login. Call right after {@code PlayerDirectory.start()}: both
     * run in order on that one thread, so the schema is migrated first. Until it ran, every login gets the "starting" screen.
     */
    public void load() {
        if (!loading.compareAndSet(false, true)) {
            return;
        }
        try {
            io.execute(this::loadNow);
        } catch (RejectedExecutionException shutDown) {
            loading.set(false);
        }
    }

    /**
     * Storage thread. Without a database there are no bans, so the login opens. If the database is up but the load fails,
     * the login stays closed (fail-closed) and the next login attempt queues another try through {@link #load()}.
     */
    private void loadNow() {
        try {
            if (!directory.storageAvailable()) {
                bans.markLoaded();
                mutes.markLoaded();
                return;
            }
            List<BanRecord> active = store.loadActiveBans(clock.getAsLong());
            bans.load(active);
            bans.markLoaded();
            log.info("Loaded " + active.size() + " active ban(s)");
            loadMutes();
            usable = true; // after the mutes, so a /mute in the start-up window cannot add a second row
        } catch (StorageException | RuntimeException e) {
            if (!loadFailureLogged) {
                loadFailureLogged = true;
                log.log(Level.SEVERE, "Could not load the active bans: logins stay closed and the load is retried on every login attempt", e);
            }
        } finally {
            loading.set(false);
        }
    }

    /**
     * A failed mute load leaves chat unblocked (a mute is no security boundary) and makes /mute, /tempmute and /unmute refuse
     * until a restart, so no mute is recorded that the chat check could not see. Bans and logins are not affected.
     */
    private void loadMutes() {
        try {
            List<MuteRecord> active = store.loadActiveMutes(clock.getAsLong());
            mutes.load(active);
            mutes.markLoaded();
            log.info("Loaded " + active.size() + " active mute(s)");
        } catch (StorageException | RuntimeException e) {
            mutes.markLoadFailed();
            log.log(Level.SEVERE, "Could not load the active mutes: muted players can chat and mute commands are refused until the next restart", e);
        }
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
        MutedPlayerArgument mutedPlayer = new MutedPlayerArgument(new KnownPlayerArgument(directory, messages), mutes, clock);
        check(commands.register(new MuteCommand(kit, service).node(), "Mute a player"), missing, "mute");
        check(commands.register(new TempmuteCommand(kit, service).node(), "Mute a player for a while"), missing, "tempmute");
        check(commands.register(new UnmuteCommand(kit, service, mutedPlayer).node(), "Unmute a player"), missing, "unmute");
        check(commands.register(new WarnCommand(kit, service).node(), "Warn a player"), missing, "warn");
        check(commands.register(new HistoryCommand(kit, history).node(), "Show the punishments of a player"), missing, "history");
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
