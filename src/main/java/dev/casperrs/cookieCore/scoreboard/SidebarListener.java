package dev.casperrs.cookieCore.scoreboard;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;

/** Joins and quits drive the sidebar; a plugin that disables takes its placeholders with it. */
final class SidebarListener implements Listener {

    private final SidebarService service;
    private final PlaceholderRegistry registry;

    SidebarListener(SidebarService service, PlaceholderRegistry registry) {
        this.service = service;
        this.registry = registry;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        service.join(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        service.quit(event.getPlayer());
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        registry.unregisterAll(event.getPlugin());
    }
}
