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
                                + "last_seen BIGINT NOT NULL)"),
                // v1 -> v2 (core-scoreboard): each player's sidebar choice
                List.of(
                        "CREATE TABLE IF NOT EXISTS " + p + "sidebar_prefs ("
                                + "uuid VARCHAR(36) NOT NULL PRIMARY KEY, "
                                + "visible BOOLEAN NOT NULL)"),
                // v2 -> v3 (core-moderation-bans): kicks and bans; one row per action, never deleted
                List.of(
                        "CREATE TABLE IF NOT EXISTS " + p + "punishments ("
                                + "id BIGINT AUTO_INCREMENT NOT NULL, "
                                + "type VARCHAR(16) NOT NULL, "
                                + "target_uuid VARCHAR(36) NOT NULL, "
                                + "target_name VARCHAR(" + JdbcPlayerStore.MAX_NAME_LENGTH + ") NOT NULL, "
                                + "actor_uuid VARCHAR(36) NULL, "
                                + "actor_name VARCHAR(" + JdbcPlayerStore.MAX_NAME_LENGTH + ") NOT NULL, "
                                + "reason VARCHAR(" + JdbcPlayerStore.MAX_REASON_LENGTH + ") NOT NULL, "
                                + "created_at BIGINT NOT NULL, "
                                + "expires_at BIGINT NULL, "
                                + "silent BOOLEAN NOT NULL, "
                                + "active BOOLEAN NOT NULL, "
                                + "removed_at BIGINT NULL, "
                                + "removed_by_uuid VARCHAR(36) NULL, "
                                + "removed_by_name VARCHAR(" + JdbcPlayerStore.MAX_NAME_LENGTH + ") NULL, "
                                + "remove_reason VARCHAR(" + JdbcPlayerStore.MAX_REASON_LENGTH + ") NULL, "
                                + "PRIMARY KEY (id))",
                        // the index name is schema-wide in H2, so it carries the table prefix
                        "CREATE INDEX IF NOT EXISTS " + p + "punishments_target ON " + p + "punishments (target_uuid, active)"));
    }

    /** The schema version this plugin version expects. */
    public static int currentVersion() {
        return all("").size();
    }
}
