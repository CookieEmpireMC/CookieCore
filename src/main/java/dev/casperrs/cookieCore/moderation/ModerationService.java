package dev.casperrs.cookieCore.moderation;

import com.mojang.brigadier.Command;
import dev.casperrs.cookieCore.Perm;
import dev.casperrs.cookieCore.api.PlayerRef;
import dev.casperrs.cookieCore.log.WarnOnce;
import dev.casperrs.cookieCore.sound.SoundEvents;
import dev.casperrs.cookieCore.sound.SoundService;
import dev.casperrs.cookieCore.storage.BanRecord;
import dev.casperrs.cookieCore.storage.MuteRecord;
import dev.casperrs.cookieCore.storage.NewPunishment;
import dev.casperrs.cookieCore.storage.PunishmentStore;
import dev.casperrs.cookieCore.storage.PunishmentType;
import dev.casperrs.cookieCore.storage.StorageException;
import dev.casperrs.cookieCore.text.Messages;
import dev.casperrs.cookieCore.text.TimeText;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerKickEvent;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Kick, ban, tempban, unban and the mute equivalents. A command calls these on the main thread. For a ban, mute or
 * their undo the database write runs first on the storage thread; only when it succeeded does the main thread update
 * the {@link BanList} or {@link MuteList}, remove a banned target, confirm to the sender and alert the others, so a
 * failed write leaves everything as it was. Kicks are
 * recorded too, but still work when the database is down. One INFO log line per action.
 */
public final class ModerationService {

    static final int MAX_REASON_LENGTH = 200;

    private final PunishmentStore store;
    private final Executor io;
    private final Executor main;
    private final BanList bans;
    private final MuteList mutes;
    private final Messages messages;
    private final SoundService sounds;
    private final Logger log;
    private final LongSupplier clock;
    private final Supplier<? extends Collection<? extends Player>> online;
    private final BooleanSupplier storageUp;
    private final WarnOnce warnOnce;
    /** Targets with a write in flight (main thread only): a second command on them waits, so one ban never gets two rows. */
    private final Set<UUID> busy = new HashSet<>();

    /** {@code io} is the storage thread, {@code main} runs a task on the main thread; {@code storageUp} says whether bans and mutes can be saved. */
    public ModerationService(PunishmentStore store, Executor io, Executor main, BanList bans, MuteList mutes, Messages messages,
                             SoundService sounds, Logger log, LongSupplier clock,
                             Supplier<? extends Collection<? extends Player>> online, BooleanSupplier storageUp) {
        this.store = store;
        this.io = io;
        this.main = main;
        this.bans = bans;
        this.mutes = mutes;
        this.messages = messages;
        this.sounds = sounds;
        this.log = log;
        this.clock = clock;
        this.online = online;
        this.storageUp = storageUp;
        this.warnOnce = new WarnOnce(log);
    }

    /** Not called {@code kick}: the architecture rules keep that call in {@code text/}. */
    public int kickOut(CommandSender sender, Player target, boolean silent, @Nullable String reasonInput) {
        Actor actor = Actor.of(sender, messages.raw("moderation.console"));
        Optional<String> reason = reasonOf(sender, reasonInput);
        if (reason.isEmpty()) {
            return 0;
        }
        if (actor.is(target.getUniqueId())) {
            return fail(sender, "moderation.error.self");
        }
        if (!storageUp.getAsBoolean()) {
            warnOnce.warn("kick-unrecorded", "The database is not available: kicks are logged here but not stored");
            afterKick(sender, actor, target, reason.get(), silent);
            return Command.SINGLE_SUCCESS;
        }
        if (!busy.add(target.getUniqueId())) {
            return fail(sender, "moderation.error.busy", player(target.getName()));
        }
        NewPunishment row = new NewPunishment(PunishmentType.KICK, target.getUniqueId(), target.getName(), actor.uuid(),
                actor.name(), reason.get(), clock.getAsLong(), null, silent);
        background(sender, target.getUniqueId(), "kick of " + target.getName(), () -> store.insert(row),
                id -> afterKick(sender, actor, target, row.reason(), silent));
        return Command.SINGLE_SUCCESS;
    }

    /** {@code length} null is a permanent ban. */
    public int ban(CommandSender sender, PlayerRef target, @Nullable Duration length, boolean silent, @Nullable String reasonInput) {
        Actor actor = Actor.of(sender, messages.raw("moderation.console"));
        Optional<String> reason = reasonOf(sender, reasonInput);
        if (reason.isEmpty()) {
            return 0;
        }
        if (!storageUp.getAsBoolean()) {
            return fail(sender, "moderation.error.storage-down");
        }
        if (actor.is(target.uuid())) {
            return fail(sender, "moderation.error.self");
        }
        long now = clock.getAsLong();
        Optional<BanRecord> existing = bans.active(target.uuid(), now);
        if (existing.isPresent()) {
            Long end = existing.get().expiresAt();
            return end == null
                    ? fail(sender, "moderation.error.already-banned", player(target.name()))
                    : fail(sender, "moderation.error.already-banned-until", player(target.name()),
                            Placeholder.unparsed("until", TimeText.until(end)));
        }
        if (!busy.add(target.uuid())) {
            return fail(sender, "moderation.error.busy", player(target.name()));
        }
        Long expires = length == null ? null : now + length.toMillis();
        NewPunishment row = new NewPunishment(PunishmentType.BAN, target.uuid(), target.name(), actor.uuid(), actor.name(),
                reason.get(), now, expires, silent);
        background(sender, target.uuid(), "ban of " + target.name(), () -> store.insert(row),
                id -> afterBan(sender, actor, new BanRecord(id, target.uuid(), target.name(), actor.name(), row.reason(), now, expires),
                        length, silent));
        return Command.SINGLE_SUCCESS;
    }

    public int unban(CommandSender sender, PlayerRef target, boolean silent, @Nullable String reasonInput) {
        Actor actor = Actor.of(sender, messages.raw("moderation.console"));
        Optional<String> reason = reasonOf(sender, reasonInput);
        if (reason.isEmpty()) {
            return 0;
        }
        if (!storageUp.getAsBoolean()) {
            return fail(sender, "moderation.error.storage-down");
        }
        long now = clock.getAsLong();
        if (bans.active(target.uuid(), now).isEmpty()) {
            return fail(sender, "moderation.error.not-banned", player(target.name()));
        }
        if (!busy.add(target.uuid())) {
            return fail(sender, "moderation.error.busy", player(target.name()));
        }
        background(sender, target.uuid(), "unban of " + target.name(),
                () -> store.deactivateBans(target.uuid(), actor.uuid(), actor.name(), reason.get(), now),
                changed -> afterUnban(sender, actor, target, reason.get(), silent));
        return Command.SINGLE_SUCCESS;
    }

    /** {@code length} null is a permanent mute. */
    public int mute(CommandSender sender, PlayerRef target, @Nullable Duration length, boolean silent, @Nullable String reasonInput) {
        Actor actor = Actor.of(sender, messages.raw("moderation.console"));
        Optional<String> reason = reasonOf(sender, reasonInput);
        if (reason.isEmpty()) {
            return 0;
        }
        if (!storageUp.getAsBoolean()) {
            return fail(sender, "moderation.error.storage-down");
        }
        if (actor.is(target.uuid())) {
            return fail(sender, "moderation.error.self");
        }
        long now = clock.getAsLong();
        if (mutes.active(target.uuid(), now).isPresent()) {
            return fail(sender, "moderation.already-muted", player(target.name()));
        }
        if (!busy.add(target.uuid())) {
            return fail(sender, "moderation.error.busy", player(target.name()));
        }
        Long expires = length == null ? null : now + length.toMillis();
        NewPunishment row = new NewPunishment(PunishmentType.MUTE, target.uuid(), target.name(), actor.uuid(), actor.name(),
                reason.get(), now, expires, silent);
        background(sender, target.uuid(), "mute of " + target.name(), () -> store.insert(row),
                id -> afterMute(sender, actor, new MuteRecord(id, target.uuid(), target.name(), actor.name(), row.reason(), now, expires),
                        length, silent));
        return Command.SINGLE_SUCCESS;
    }

    public int unmute(CommandSender sender, PlayerRef target, boolean silent, @Nullable String reasonInput) {
        Actor actor = Actor.of(sender, messages.raw("moderation.console"));
        Optional<String> reason = reasonOf(sender, reasonInput);
        if (reason.isEmpty()) {
            return 0;
        }
        if (!storageUp.getAsBoolean()) {
            return fail(sender, "moderation.error.storage-down");
        }
        long now = clock.getAsLong();
        if (mutes.active(target.uuid(), now).isEmpty()) {
            return fail(sender, "moderation.not-muted", player(target.name()));
        }
        if (!busy.add(target.uuid())) {
            return fail(sender, "moderation.error.busy", player(target.name()));
        }
        background(sender, target.uuid(), "unmute of " + target.name(),
                () -> store.deactivateMutes(target.uuid(), actor.uuid(), actor.name(), reason.get(), now),
                changed -> afterUnmute(sender, actor, target, reason.get(), silent));
        return Command.SINGLE_SUCCESS;
    }

    private void afterMute(CommandSender sender, Actor actor, MuteRecord mute, @Nullable Duration length, boolean silent) {
        mutes.put(mute);
        TagResolver[] placeholders = placeholders(actor, mute.targetName(), mute.reason(), length, mute.expiresAt());
        done(sender, length == null ? "moderation.mute-done" : "moderation.mute-temp-done", placeholders);
        alert(sender, mute.targetUuid(), length == null ? "moderation.mute-broadcast" : "moderation.mute-temp-broadcast", silent, placeholders);
        logAction(length == null ? "MUTE" : "TEMPMUTE", actor, mute.targetName(), mute.reason(), length, silent);
    }

    private void afterUnmute(CommandSender sender, Actor actor, PlayerRef target, String reason, boolean silent) {
        mutes.remove(target.uuid());
        TagResolver[] placeholders = placeholders(actor, target.name(), reason, null, null);
        done(sender, "moderation.unmute-done", placeholders);
        alert(sender, target.uuid(), "moderation.unmute-broadcast", silent, placeholders);
        logAction("UNMUTE", actor, target.name(), reason, null, silent);
    }

    private void afterKick(CommandSender sender, Actor actor, Player target, String reason, boolean silent) {
        if (!target.isOnline()) {
            fail(sender, "moderation.error.not-online");
            return;
        }
        messages.disconnect(target, messages.kickScreen(actor.name(), reason), PlayerKickEvent.Cause.KICKED);
        TagResolver[] placeholders = placeholders(actor, target.getName(), reason, null, null);
        done(sender, "moderation.kicked", placeholders);
        alert(sender, target.getUniqueId(), "moderation.broadcast.kick", silent, placeholders);
        logAction("KICK", actor, target.getName(), reason, null, silent);
    }

    private void afterBan(CommandSender sender, Actor actor, BanRecord ban, @Nullable Duration length, boolean silent) {
        bans.put(ban);
        findOnline(ban.targetUuid()).ifPresent(target ->
                messages.disconnect(target, messages.banScreen(ban, clock.getAsLong()), PlayerKickEvent.Cause.BANNED));
        TagResolver[] placeholders = placeholders(actor, ban.targetName(), ban.reason(), length, ban.expiresAt());
        done(sender, length == null ? "moderation.banned" : "moderation.tempbanned", placeholders);
        alert(sender, ban.targetUuid(), length == null ? "moderation.broadcast.ban" : "moderation.broadcast.tempban", silent, placeholders);
        logAction(length == null ? "BAN" : "TEMPBAN", actor, ban.targetName(), ban.reason(), length, silent);
    }

    private void afterUnban(CommandSender sender, Actor actor, PlayerRef target, String reason, boolean silent) {
        bans.remove(target.uuid());
        TagResolver[] placeholders = placeholders(actor, target.name(), reason, null, null);
        done(sender, "moderation.unbanned", placeholders);
        alert(sender, target.uuid(), "moderation.broadcast.unban", silent, placeholders);
        logAction("UNBAN", actor, target.name(), reason, null, silent);
    }

    /** Everyone online except the target, or when {@code silent} only staff with {@link Perm#STAFF_NOTIFY}; staff hear the notify sound. */
    private void alert(CommandSender sender, UUID target, String key, boolean silent, TagResolver... placeholders) {
        TagResolver silentWrap = silent
                ? Placeholder.component("message", messages.component(key, placeholders))
                : TagResolver.empty();
        for (Player each : online.get()) {
            boolean staff = each.hasPermission(Perm.STAFF_NOTIFY);
            if (each.getUniqueId().equals(target) || (silent && (!staff || each.equals(sender)))) {
                continue;
            }
            if (silent) {
                messages.send(each, "moderation.alert.silent", silentWrap);
            } else {
                messages.send(each, key, placeholders);
            }
            if (staff && !each.equals(sender)) {
                sounds.play(each, SoundEvents.UI_NOTIFY);
            }
        }
    }

    private static TagResolver[] placeholders(Actor actor, String target, String reason, @Nullable Duration length, @Nullable Long expires) {
        List<TagResolver> out = new ArrayList<>(List.of(Placeholder.unparsed("staff", actor.name()), player(target),
                Placeholder.unparsed("reason", reason)));
        if (length != null && expires != null) {
            out.add(Placeholder.unparsed("duration", TimeText.remaining(length)));
            out.add(Placeholder.unparsed("until", TimeText.until(expires)));
        }
        return out.toArray(TagResolver[]::new);
    }

    private void logAction(String kind, Actor actor, String target, String reason, @Nullable Duration length, boolean silent) {
        log.info(kind + ": " + target + " by " + actor.name() + ", reason: " + reason
                + (length == null ? "" : ", for " + TimeText.remaining(length)) + (silent ? " (silent)" : ""));
    }

    private Optional<String> reasonOf(CommandSender sender, @Nullable String input) {
        if (input == null || input.isBlank()) {
            return Optional.of(messages.raw("moderation.default-reason"));
        }
        String reason = input.strip();
        if (reason.length() > MAX_REASON_LENGTH) {
            fail(sender, "moderation.error.reason-too-long", Placeholder.unparsed("count", String.valueOf(MAX_REASON_LENGTH)));
            return Optional.empty();
        }
        return Optional.of(reason);
    }

    private Optional<Player> findOnline(UUID id) {
        for (Player each : online.get()) {
            if (each.getUniqueId().equals(id)) {
                return Optional.of(each);
            }
        }
        return Optional.empty();
    }

    private interface Call<T> {
        T run() throws StorageException;
    }

    /** Runs {@code call} on the storage thread, then {@code onSuccess} on the main thread; a failure tells the sender and changes nothing else. */
    private <T> void background(CommandSender sender, UUID key, String what, Call<T> call, Consumer<T> onSuccess) {
        try {
            io.execute(() -> {
                try {
                    T outcome = call.run();
                    handBack(() -> finish(sender, key, null, what, () -> onSuccess.accept(outcome)));
                } catch (Throwable failure) { // an Error must free the target too, then keeps propagating
                    handBack(() -> finish(sender, key, failure, what, () -> { }));
                    if (failure instanceof Error error) {
                        throw error;
                    }
                }
            });
        } catch (RejectedExecutionException shutDown) {
            busy.remove(key);
            fail(sender, "moderation.error.failed");
        }
    }

    /** Main thread: frees the target, then reports the failure or runs {@code onSuccess}. */
    private void finish(CommandSender sender, UUID key, @Nullable Throwable failure, String what, Runnable onSuccess) {
        try {
            if (failure != null) {
                log.log(Level.WARNING, "Could not record the " + what + " in the database; nothing was changed", failure);
                fail(sender, "moderation.error.failed");
            } else {
                onSuccess.run();
            }
        } finally {
            busy.remove(key);
        }
    }

    private void handBack(Runnable task) {
        try {
            main.execute(task);
        } catch (RuntimeException gone) {
            // the plugin is disabling: nobody is waiting for the answer any more
        }
    }

    private int fail(CommandSender sender, String key, TagResolver... placeholders) {
        messages.send(sender, key, placeholders);
        sounds.play(sender, SoundEvents.UI_ERROR);
        return 0;
    }

    private void done(CommandSender sender, String key, TagResolver... placeholders) {
        messages.send(sender, key, placeholders);
        sounds.play(sender, SoundEvents.UI_CONFIRM);
    }

    private static TagResolver player(String name) {
        return Placeholder.unparsed("player", name);
    }
}
