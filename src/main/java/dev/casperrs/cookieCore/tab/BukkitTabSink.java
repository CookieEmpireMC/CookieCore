package dev.casperrs.cookieCore.tab;

import dev.casperrs.cookieCore.text.TabText;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

import java.util.Collection;

/** {@link TabSink} on Paper. Paper does not skip a repeated value: every call is a packet, so the service only calls on a change. */
final class BukkitTabSink implements TabSink {

    @Override
    public void listName(Player player, @Nullable Component name) {
        TabText.listName(player, name);
    }

    @Override
    public void order(Player player, int order) {
        player.setPlayerListOrder(order);
    }

    @Override
    public int order(Player player) {
        return player.getPlayerListOrder();
    }

    @Override
    public void headerAndFooter(Player player, Component header, Component footer) {
        TabText.headerAndFooter(player, header, footer);
    }

    @Override
    public int ping(Player player) {
        return player.getPing();
    }

    @Override
    public double tps() {
        return Bukkit.getTPS()[0];
    }

    @Override
    public int online() {
        return Bukkit.getOnlinePlayers().size();
    }

    @Override
    public int maxPlayers() {
        return Bukkit.getMaxPlayers();
    }

    @Override
    public Collection<? extends Player> onlinePlayers() {
        return Bukkit.getOnlinePlayers();
    }
}
