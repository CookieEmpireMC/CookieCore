package dev.casperrs.cookieCore.moderation;

import dev.casperrs.cookieCore.storage.BanRecord;
import dev.casperrs.cookieCore.text.Messages;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;

import java.util.Optional;
import java.util.function.LongSupplier;

/** Keeps banned players out. Memory only: a hash lookup per login, never the database. */
public final class ModerationListener implements Listener {

    private final BanList bans;
    private final Messages messages;
    private final LongSupplier clock;
    private final Runnable retryLoad;

    public ModerationListener(BanList bans, Messages messages, LongSupplier clock) {
        this(bans, messages, clock, () -> { });
    }

    /** {@code retryLoad} must only queue work (it runs on a login thread) and is called for every login while the bans are not loaded. */
    public ModerationListener(BanList bans, Messages messages, LongSupplier clock, Runnable retryLoad) {
        this.bans = bans;
        this.messages = messages;
        this.clock = clock;
        this.retryLoad = retryLoad;
    }

    /**
     * Runs on a login thread, one per connecting player, concurrently. HIGHEST so that no plugin that runs before
     * this one can allow the login again.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (!bans.loaded()) {
            retryLoad.run();
            if (event.getLoginResult() == AsyncPlayerPreLoginEvent.Result.ALLOWED) {
                messages.denyStarting(event); // another plugin's refusal stays as it is
            }
            return;
        }
        long now = clock.getAsLong();
        bans.active(event.getUniqueId(), now).ifPresent(ban -> messages.denyBanned(event, ban, now));
    }

    /** A ban made while the player was still logging in has no player to remove; catch them here. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        long now = clock.getAsLong();
        Optional<BanRecord> ban = bans.active(event.getPlayer().getUniqueId(), now);
        ban.ifPresent(b -> messages.disconnect(event.getPlayer(), messages.banScreen(b, now), PlayerKickEvent.Cause.BANNED));
    }
}
