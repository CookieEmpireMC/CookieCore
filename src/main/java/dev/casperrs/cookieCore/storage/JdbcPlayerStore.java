package dev.casperrs.cookieCore.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * JDBC {@link PlayerStore} for H2 and MariaDB (plain SQL that both accept). Holds one connection and reopens it when it
 * goes stale. Single-threaded by contract. The schema is versioned by {@link SchemaMigrator} with {@link Migrations}.
 *
 * <p>Tables (prefix {@code core_} by default): {@code players(uuid, name, first_seen, last_seen)} and
 * {@code sidebar_prefs(uuid, visible)}.
 */
public final class JdbcPlayerStore implements PlayerStore, SidebarStore {

    static final int MAX_NAME_LENGTH = 32;

    private final ConnectionFactory connections;
    private final String p;
    private final Logger log;
    private Connection connection;

    public JdbcPlayerStore(ConnectionFactory connections, String tablePrefix) {
        this(connections, tablePrefix, Logger.getLogger("CookieCore"));
    }

    public JdbcPlayerStore(ConnectionFactory connections, String tablePrefix, Logger log) {
        if (!tablePrefix.matches(DatabaseSettings.PREFIX_PATTERN)) {
            throw new IllegalArgumentException("bad table prefix '" + tablePrefix + "'");
        }
        this.connections = connections;
        this.p = tablePrefix;
        this.log = log;
    }

    @Override
    public void init() throws StorageException {
        try {
            SchemaMigrator.migrate(connection(), p, Migrations.all(p));
        } catch (SQLException e) {
            throw new StorageException("Could not initialise the database schema", e);
        }
    }

    /** Current schema version, 0 for a fresh database. */
    public int schemaVersion() throws StorageException {
        try {
            Connection c = connection();
            try (Statement st = c.createStatement()) {
                st.executeUpdate("CREATE TABLE IF NOT EXISTS " + p + "schema_version (id INT NOT NULL PRIMARY KEY, version INT NOT NULL)");
            }
            return SchemaMigrator.version(c, p);
        } catch (SQLException e) {
            throw new StorageException("Could not read the schema version", e);
        }
    }

    @Override
    public void upsertSeen(UUID uuid, String name, long now) throws StorageException {
        String id = uuid.toString();
        String stored = name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH) : name;
        try {
            Connection c = connection();
            try (PreparedStatement up = c.prepareStatement("UPDATE " + p + "players SET name = ?, last_seen = ? WHERE uuid = ?")) {
                up.setString(1, stored);
                up.setLong(2, now);
                up.setString(3, id);
                if (up.executeUpdate() > 0) {
                    return;
                }
            }
            try (PreparedStatement ins = c.prepareStatement("INSERT INTO " + p + "players (uuid, name, first_seen, last_seen) VALUES (?, ?, ?, ?)")) {
                ins.setString(1, id);
                ins.setString(2, stored);
                ins.setLong(3, now);
                ins.setLong(4, now);
                ins.executeUpdate();
            }
        } catch (SQLException e) {
            invalidate();
            throw new StorageException("Could not save player " + name, e);
        }
    }

    @Override
    public List<PlayerRecord> loadAll() throws StorageException {
        List<PlayerRecord> out = new ArrayList<>();
        try {
            Connection c = connection();
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT uuid, name, first_seen, last_seen FROM " + p + "players ORDER BY last_seen ASC")) {
                int skipped = 0;
                while (rs.next()) {
                    try {
                        out.add(record(rs));
                    } catch (IllegalArgumentException corrupt) {
                        skipped++; // a hand-edited or damaged row must not cost every other player
                    }
                }
                if (skipped > 0) {
                    log.warning("Skipped " + skipped + " row(s) in " + p + "players with an invalid uuid");
                }
            }
        } catch (SQLException e) {
            invalidate();
            throw new StorageException("Could not load the players", e);
        }
        return out;
    }

    @Override
    public Optional<PlayerRecord> findByName(String name) throws StorageException {
        try {
            Connection c = connection();
            try (PreparedStatement st = c.prepareStatement("SELECT uuid, name, first_seen, last_seen FROM " + p
                    + "players WHERE LOWER(name) = ? ORDER BY last_seen DESC LIMIT 1")) {
                st.setString(1, name.toLowerCase(Locale.ROOT));
                try (ResultSet rs = st.executeQuery()) {
                    return rs.next() ? Optional.of(record(rs)) : Optional.empty();
                }
            }
        } catch (IllegalArgumentException e) {
            throw new StorageException("Player " + name + " has an invalid uuid in the database", e);
        } catch (SQLException e) {
            invalidate();
            throw new StorageException("Could not look up player " + name, e);
        }
    }

    @Override
    public boolean loadVisible(UUID uuid) throws StorageException {
        try {
            Connection c = connection();
            try (PreparedStatement st = c.prepareStatement("SELECT visible FROM " + p + "sidebar_prefs WHERE uuid = ?")) {
                st.setString(1, uuid.toString());
                try (ResultSet rs = st.executeQuery()) {
                    return !rs.next() || rs.getBoolean(1);
                }
            }
        } catch (SQLException e) {
            invalidate();
            throw new StorageException("Could not load the sidebar choice of " + uuid, e);
        }
    }

    @Override
    public void saveVisible(UUID uuid, boolean visible) throws StorageException {
        String id = uuid.toString();
        try {
            Connection c = connection();
            try (PreparedStatement up = c.prepareStatement("UPDATE " + p + "sidebar_prefs SET visible = ? WHERE uuid = ?")) {
                up.setBoolean(1, visible);
                up.setString(2, id);
                if (up.executeUpdate() > 0) {
                    return;
                }
            }
            try (PreparedStatement ins = c.prepareStatement("INSERT INTO " + p + "sidebar_prefs (uuid, visible) VALUES (?, ?)")) {
                ins.setString(1, id);
                ins.setBoolean(2, visible);
                ins.executeUpdate();
            }
        } catch (SQLException e) {
            invalidate();
            throw new StorageException("Could not save the sidebar choice of " + uuid, e);
        }
    }

    private static PlayerRecord record(ResultSet rs) throws SQLException {
        return new PlayerRecord(UUID.fromString(rs.getString(1)), rs.getString(2), rs.getLong(3), rs.getLong(4));
    }

    private Connection connection() throws SQLException {
        if (connection == null || !connection.isValid(2)) {
            invalidate();
            connection = connections.open();
        }
        return connection;
    }

    private void invalidate() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
                // already broken
            }
            connection = null;
        }
    }

    @Override
    public void close() {
        invalidate();
    }
}
