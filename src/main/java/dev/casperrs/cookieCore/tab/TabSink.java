package dev.casperrs.cookieCore.tab;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

import java.util.Collection;

/** Every Paper call the tab list makes, so the diff logic can be tested without a server. Main thread only. */
public interface TabSink {

    /** Sends the name to everybody who sees the player; null = the plain name. */
    void listName(Player player, @Nullable Component name);

    /** Higher is listed first; never negative. */
    void order(Player player, int order);

    int order(Player player);

    void headerAndFooter(Player player, Component header, Component footer);

    /** Milliseconds; the player must be connected. */
    int ping(Player player);

    /** The one-minute average, not capped. */
    double tps();

    int online();

    int maxPlayers();

    Collection<? extends Player> onlinePlayers();
}
