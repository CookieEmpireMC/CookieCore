package dev.casperrs.cookieCore.rank;

import org.bukkit.Bukkit;

import java.util.logging.Logger;

/** Chooses the rank source of this server. */
final class RankSources {

    private static final String LUCKPERMS = "LuckPerms";

    private RankSources() {
    }

    /** LuckPerms' prefix when the plugin is running, then {@code fallback}; just {@code fallback} when it is not or cannot be used. */
    static RankSource create(RankSource fallback, Logger log) {
        if (Bukkit.getPluginManager().isPluginEnabled(LUCKPERMS)) {
            try {
                return new LuckPermsRankSource().orElse(fallback);
            } catch (LinkageError | RuntimeException e) {
                log.warning("LuckPerms is installed but its prefixes cannot be read (" + e + "); using the ranks in chat.yml");
            }
        }
        return fallback;
    }
}
