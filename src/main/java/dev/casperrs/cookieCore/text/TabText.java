package dev.casperrs.cookieCore.text;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/** The two calls that put text in a player's tab list. Like all player-facing text they live in {@code text/}; callers send only on a change. */
public final class TabText {

    private TabText() {
    }

    /** Sends the name to everybody who sees the player; null = the plain name. */
    public static void listName(Player player, @Nullable Component name) {
        player.playerListName(name);
    }

    public static void headerAndFooter(Player player, Component header, Component footer) {
        player.sendPlayerListHeaderAndFooter(header, footer);
    }
}
