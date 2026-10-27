package dev.casperrs.cookieCore;

import dev.casperrs.cookieCore.api.CookieCoreApi;
import dev.casperrs.cookieCore.api.PlayerLookup;
import dev.casperrs.cookieCore.api.ScoreboardPlaceholders;

/** The {@link CookieCoreApi} that goes into Bukkit's services manager. */
final class CoreService implements CookieCoreApi {

    private final String version;
    private final PlayerLookup players;
    private final ScoreboardPlaceholders scoreboardPlaceholders;

    CoreService(String version, PlayerLookup players, ScoreboardPlaceholders scoreboardPlaceholders) {
        this.version = version;
        this.players = players;
        this.scoreboardPlaceholders = scoreboardPlaceholders;
    }

    @Override
    public String version() {
        return version;
    }

    @Override
    public PlayerLookup players() {
        return players;
    }

    @Override
    public ScoreboardPlaceholders scoreboardPlaceholders() {
        return scoreboardPlaceholders;
    }
}
