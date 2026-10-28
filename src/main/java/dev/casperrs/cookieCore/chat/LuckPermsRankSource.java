package dev.casperrs.cookieCore.chat;

import dev.casperrs.cookieCore.text.ChatText;
import net.kyori.adventure.text.Component;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Optional;

/**
 * The prefix LuckPerms holds for the player. The only class that touches LuckPerms types, and only {@link RankSources} creates
 * it, after checking that the plugin is there, so Core runs without LuckPerms on the class path.
 */
final class LuckPermsRankSource implements RankSource {

    private final LuckPerms luckPerms;

    LuckPermsRankSource() {
        RegisteredServiceProvider<LuckPerms> provider = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        if (provider == null) {
            throw new IllegalStateException("the LuckPerms API is not registered");
        }
        luckPerms = provider.getProvider();
    }

    /** Reads LuckPerms' cache: a user that is not loaded yet gives no prefix instead of waiting for the database. */
    @Override
    public Optional<Component> prefix(Player player) {
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) {
            return Optional.empty();
        }
        String raw = user.getCachedData().getMetaData().getPrefix();
        return raw == null || raw.isBlank() ? Optional.empty() : Optional.of(ChatText.legacy(raw));
    }
}
