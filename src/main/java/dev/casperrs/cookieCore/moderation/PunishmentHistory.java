package dev.casperrs.cookieCore.moderation;

import com.mojang.brigadier.Command;
import dev.casperrs.cookieCore.api.PlayerRef;
import dev.casperrs.cookieCore.sound.SoundEvents;
import dev.casperrs.cookieCore.sound.SoundService;
import dev.casperrs.cookieCore.storage.HistoryEntry;
import dev.casperrs.cookieCore.storage.PunishmentStore;
import dev.casperrs.cookieCore.storage.PunishmentType;
import dev.casperrs.cookieCore.text.Messages;
import dev.casperrs.cookieCore.text.TimeText;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/** The {@code /history} output: read on the storage thread, shown on the main thread. */
public final class PunishmentHistory {

    static final int PAGE_SIZE = 8;

    private final PunishmentStore store;
    private final Executor io;
    private final Executor main;
    private final Messages messages;
    private final SoundService sounds;
    private final Logger log;
    private final LongSupplier clock;
    private final BooleanSupplier storageUp;

    public PunishmentHistory(PunishmentStore store, Executor io, Executor main, Messages messages, SoundService sounds,
                             Logger log, LongSupplier clock, BooleanSupplier storageUp) {
        this.store = store;
        this.io = io;
        this.main = main;
        this.messages = messages;
        this.sounds = sounds;
        this.log = log;
        this.clock = clock;
        this.storageUp = storageUp;
    }

    /** {@code page} counts from 1. */
    public int show(CommandSender sender, PlayerRef target, int page) {
        if (!storageUp.getAsBoolean()) {
            return fail(sender, "moderation.error.storage-down");
        }
        try {
            io.execute(() -> read(sender, target, page));
        } catch (RejectedExecutionException shutDown) {
            return fail(sender, "moderation.error.failed");
        }
        return Command.SINGLE_SUCCESS;
    }

    private void read(CommandSender sender, PlayerRef target, int page) {
        List<HistoryEntry> rows;
        try {
            // one row more than a page tells whether there is a next page
            rows = store.history(target.uuid(), PAGE_SIZE + 1, (page - 1) * PAGE_SIZE);
        } catch (Exception failure) {
            log.log(Level.WARNING, "Could not read the history of " + target.name(), failure);
            handBack(() -> fail(sender, "moderation.error.failed"));
            return;
        }
        handBack(() -> render(sender, target, page, rows));
    }

    private void render(CommandSender sender, PlayerRef target, int page, List<HistoryEntry> rows) {
        TagResolver player = Placeholder.unparsed("player", target.name());
        if (rows.isEmpty()) {
            messages.send(sender, "moderation.history-empty", player);
            return;
        }
        long now = clock.getAsLong();
        messages.send(sender, "moderation.history-header", player, Placeholder.unparsed("page", String.valueOf(page)));
        for (HistoryEntry row : rows.subList(0, Math.min(rows.size(), PAGE_SIZE))) {
            boolean temp = row.expiresAt() != null && lasting(row.type());
            messages.send(sender, temp ? "moderation.history-entry-temp" : "moderation.history-entry", entry(row, now));
        }
        if (rows.size() > PAGE_SIZE) {
            messages.send(sender, "moderation.history-more", player, Placeholder.unparsed("next", String.valueOf(page + 1)));
        }
    }

    private TagResolver entry(HistoryEntry row, long now) {
        Long end = row.expiresAt();
        String length = end == null ? "" : TimeText.remaining(Duration.ofMillis(end - row.createdAt()));
        return TagResolver.resolver(
                Placeholder.unparsed("id", String.valueOf(row.id())),
                Placeholder.component("type", messages.component(typeKey(row))),
                Placeholder.component("status", messages.component(statusKey(row, now))),
                Placeholder.unparsed("date", TimeText.until(row.createdAt())),
                Placeholder.unparsed("staff", row.actorName()),
                Placeholder.unparsed("reason", row.reason()),
                Placeholder.unparsed("length", length));
    }

    private static boolean lasting(PunishmentType type) {
        return type == PunishmentType.BAN || type == PunishmentType.MUTE;
    }

    private static String typeKey(HistoryEntry row) {
        boolean temp = row.expiresAt() != null;
        return switch (row.type()) {
            case KICK -> "moderation.history-type.kick";
            case BAN -> temp ? "moderation.history-type.tempban" : "moderation.history-type.ban";
            case MUTE -> temp ? "moderation.history-type.tempmute" : "moderation.history-type.mute";
            case WARN -> "moderation.history-type.warn";
        };
    }

    private static String statusKey(HistoryEntry row, long now) {
        if (!lasting(row.type())) {
            return "moderation.history-status.one-off";
        }
        if (!row.active()) {
            return "moderation.history-status.lifted";
        }
        return row.expiresAt() != null && row.expiresAt() <= now
                ? "moderation.history-status.expired"
                : "moderation.history-status.active";
    }

    private void handBack(Runnable task) {
        try {
            main.execute(task);
        } catch (RuntimeException gone) {
            // the plugin is disabling: nobody is waiting for the answer any more
        }
    }

    private int fail(CommandSender sender, String key) {
        messages.send(sender, key);
        sounds.play(sender, SoundEvents.UI_ERROR);
        return 0;
    }
}
