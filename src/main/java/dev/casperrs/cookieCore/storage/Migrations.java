package dev.casperrs.cookieCore.storage;

import java.util.List;

/**
 * The schema history of CookieCore. Never edit a released migration: append a new list (every feature that
 * adds tables appends exactly one). Plain SQL that both H2 and MariaDB accept.
 */
public final class Migrations {

    private Migrations() {
    }

    /** Migration {@code i} brings the schema from version {@code i} to {@code i + 1}; the list size is the current version. */
    public static List<List<String>> all(String p) {
        return List.of(
                // v0 -> v1 (core-foundation): every player Core has seen join
                List.of(
                        "CREATE TABLE IF NOT EXISTS " + p + "players ("
                                + "uuid VARCHAR(36) NOT NULL PRIMARY KEY, "
                                + "name VARCHAR(" + JdbcPlayerStore.MAX_NAME_LENGTH + ") NOT NULL, "
                                + "first_seen BIGINT NOT NULL, "
                                + "last_seen BIGINT NOT NULL)"));
    }

    /** The schema version this plugin version expects. */
    public static int currentVersion() {
        return all("").size();
    }
}
