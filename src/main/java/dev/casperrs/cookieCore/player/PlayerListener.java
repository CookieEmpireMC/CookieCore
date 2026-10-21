package dev.casperrs.cookieCore.player;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Keeps the {@link PlayerDirectory} (and so the {@code players} table) up to date. The handlers only update memory and
 * queue the write; nothing here waits for the database.
 */
public final class PlayerListener implements Listener {

    private final PlayerDirectory directory;

    public PlayerListener(PlayerDirectory directory) {
        this.directory = directory;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        directory.seen(event.getPlayer().getUniqueId(), event.getPlayer().getName());
    }

    /** Refreshes {@code last_seen} so "last seen" is the moment they left. */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        directory.seen(event.getPlayer().getUniqueId(), event.getPlayer().getName());
    }
}
