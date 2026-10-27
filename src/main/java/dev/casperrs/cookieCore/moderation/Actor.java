package dev.casperrs.cookieCore.moderation;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/** Who did it: a player, or the console (no uuid) under the configured console name. */
record Actor(@Nullable UUID uuid, String name) {

    static Actor of(CommandSender sender, String consoleName) {
        return sender instanceof Player player ? new Actor(player.getUniqueId(), player.getName()) : new Actor(null, consoleName);
    }

    boolean is(UUID other) {
        return other.equals(uuid);
    }
}
