package dev.casperrs.cookieCore.gamemode;

import dev.casperrs.cookieCore.Perm;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Who turned {@code /fly} on (in memory only; a relog resets it). The server drops {@code allowFlight} on every switch to
 * survival or adventure, so the flight is given back one tick after the switch. Main thread only.
 */
public final class FlyTracker implements Listener {

    private final Set<UUID> enabled = new HashSet<>();
    private final Consumer<Runnable> nextTick;

    public FlyTracker(Consumer<Runnable> nextTick) {
        this.nextTick = nextTick;
    }

    /** Lets the player fly, and start flying when they are in the air. */
    void enable(Player player) {
        enabled.add(player.getUniqueId());
        player.setAllowFlight(true);
        if (!player.isOnGround()) {
            player.setFlying(true);
        }
    }

    void disable(Player player) {
        enabled.remove(player.getUniqueId());
        player.setAllowFlight(false);
    }

    /** MONITOR: the event is fired before the change, so the player still has the old mode and the old flying state. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        if (!enabled.contains(player.getUniqueId()) || GameModes.flies(event.getNewGameMode())) {
            return;
        }
        boolean wasFlying = player.isFlying();
        nextTick.accept(() -> reapply(player, wasFlying));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (enabled.remove(player.getUniqueId()) && !GameModes.flies(player.getGameMode())) {
            player.setAllowFlight(false); // abilities are saved with the player, so it would outlive the session
        }
    }

    private void reapply(Player player, boolean wasFlying) {
        if (!player.isOnline() || !enabled.contains(player.getUniqueId())) {
            return;
        }
        if (!player.hasPermission(Perm.FLY)) {
            enabled.remove(player.getUniqueId());
            return;
        }
        if (GameModes.flies(player.getGameMode())) {
            return;
        }
        player.setAllowFlight(true);
        if (wasFlying) {
            player.setFlying(true);
        }
    }
}
