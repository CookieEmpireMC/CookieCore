package dev.casperrs.cookieCore.rank;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.Optional;

/** Where the prefix in front of a name and the position in the tab list come from. May be called on the chat thread, so it never blocks. */
@FunctionalInterface
public interface RankSource {

    /** Empty when this source has no prefix for the player. */
    Optional<Component> prefix(Player player);

    /** Position in the tab list: higher is listed first, never negative, 0 = no rank. */
    default int order(Player player) {
        return 0;
    }

    /** This source first, {@code other} when this one has nothing; the order comes from the same source as the prefix. */
    default RankSource orElse(RankSource other) {
        RankSource first = this;
        return new RankSource() {
            @Override
            public Optional<Component> prefix(Player player) {
                return first.prefix(player).or(() -> other.prefix(player));
            }

            @Override
            public int order(Player player) {
                int own = first.order(player);
                return own > 0 || first.prefix(player).isPresent() ? own : other.order(player);
            }
        };
    }
}
