package dev.casperrs.cookieCore;

import dev.casperrs.cookieCore.api.CookieCoreApi;
import dev.casperrs.cookieCore.api.PlayerLookup;

/** The {@link CookieCoreApi} that goes into Bukkit's services manager. */
final class CoreService implements CookieCoreApi {

    private final String version;
    private final PlayerLookup players;

    CoreService(String version, PlayerLookup players) {
        this.version = version;
        this.players = players;
    }

    @Override
    public String version() {
        return version;
    }

    @Override
    public PlayerLookup players() {
        return players;
    }
}
