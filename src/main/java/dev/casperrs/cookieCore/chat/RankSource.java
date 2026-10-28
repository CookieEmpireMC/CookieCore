package dev.casperrs.cookieCore.chat;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.Optional;

/** Where the prefix in front of a chat name comes from. May be called on the chat thread, so it never blocks. */
@FunctionalInterface
public interface RankSource {

    /** Empty when this source has no prefix for the player. */
    Optional<Component> prefix(Player player);

    /** This source first, {@code other} when this one has nothing. */
    default RankSource orElse(RankSource other) {
        return player -> prefix(player).or(() -> other.prefix(player));
    }
}
