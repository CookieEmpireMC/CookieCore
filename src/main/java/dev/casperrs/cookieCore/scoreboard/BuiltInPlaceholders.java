package dev.casperrs.cookieCore.scoreboard;

import java.util.List;

/** The placeholders Core itself fills in. Other plugins cannot register these keys. */
final class BuiltInPlaceholders {

    static final String PLAYER = "player";
    static final String SERVER = "server";
    static final String ONLINE = "online";
    static final String MAX = "max";
    static final String PING = "ping";

    static final List<String> ALL = List.of(PLAYER, SERVER, ONLINE, MAX, PING);

    private BuiltInPlaceholders() {
    }
}
