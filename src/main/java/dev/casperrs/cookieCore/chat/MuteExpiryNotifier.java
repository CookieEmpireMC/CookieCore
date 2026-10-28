package dev.casperrs.cookieCore.chat;

import dev.casperrs.cookieCore.moderation.MuteList;
import dev.casperrs.cookieCore.storage.MuteRecord;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Tells an online player once when their tempmute has run out. It watches only the tempmutes it saw active while the player
 * was online, so a mute that ended while they were away, a permanent mute, and an {@code /unmute} (the record is gone) never
 * produce a message. Reads {@link MuteList} in memory only. Main thread.
 *
 * <p>The repeating task exists only while someone is online: it starts at a join ({@link #start}) and stops itself when the
 * server is empty.
 */
public final class MuteExpiryNotifier {

    /** Starts a repeating main-thread task (period in ticks) and returns the function that cancels it. */
    @FunctionalInterface
    public interface Repeater {
        Runnable repeat(Runnable task, long periodTicks);
    }

    static final long PERIOD_TICKS = 20L;

    private final MuteList mutes;
    private final LongSupplier clock;
    private final Supplier<? extends Collection<? extends Player>> online;
    private final Consumer<Player> notice;
    private final Repeater repeater;
    private final Map<UUID, MuteRecord> watched = new HashMap<>();
    private @Nullable Runnable cancel;

    /** {@code notice} tells the player; it runs on the main thread. */
    public MuteExpiryNotifier(MuteList mutes, LongSupplier clock, Supplier<? extends Collection<? extends Player>> online,
                              Consumer<Player> notice, Repeater repeater) {
        this.mutes = mutes;
        this.clock = clock;
        this.online = online;
        this.notice = notice;
        this.repeater = repeater;
    }

    /** Starts the task if it is not running. Call when a player joins. */
    public void start() {
        if (cancel == null) {
            cancel = repeater.repeat(this::tick, PERIOD_TICKS);
        }
    }

    public void stop() {
        if (cancel != null) {
            cancel.run();
            cancel = null;
        }
        watched.clear();
    }

    void tick() {
        Collection<? extends Player> players = online.get();
        if (players.isEmpty()) {
            stop();
            return;
        }
        long now = clock.getAsLong();
        for (Player player : players) {
            UUID id = player.getUniqueId();
            MuteRecord active = mutes.active(id, now).orElse(null);
            if (active != null) {
                if (active.expiresAt() != null) {
                    watched.put(id, active);
                } else {
                    watched.remove(id);
                }
            } else if (watched.remove(id) != null && mutes.recorded(id).filter(record -> record.expired(now)).isPresent()) {
                notice.accept(player);
            }
        }
        if (watched.isEmpty()) {
            return;
        }
        Set<UUID> present = players.stream().map(Player::getUniqueId).collect(Collectors.toSet());
        watched.keySet().removeIf(id -> !present.contains(id));
    }
}
