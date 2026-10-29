package dev.casperrs.cookieCore.tab;

import dev.casperrs.cookieCore.rank.Ranks;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Joins and quits drive the tab list. The name and position are set inside the join event, before the server sends the
 * player-info packets, so nobody sees the plain name flash by.
 */
final class TabListener implements Listener {

    private final TabService service;
    private final Ranks ranks;

    TabListener(TabService service, Ranks ranks) {
        this.service = service;
        this.ranks = ranks;
    }

    /** After the chat's join handler (HIGH), which resolves the rank first when chat is on. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        ranks.join(event.getPlayer());
        service.join(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        service.quit(event.getPlayer());
        ranks.quit(event.getPlayer().getUniqueId());
    }
}
