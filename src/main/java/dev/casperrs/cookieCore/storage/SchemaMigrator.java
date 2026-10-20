package dev.casperrs.cookieCore.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * Brings a database to the schema version a list of migrations describes. The version lives in
 * {@code <prefix>schema_version (id, version)}, one row. Migration {@code i} (a list of SQL statements) moves the schema
 * from version {@code i} to {@code i + 1} and runs in a transaction together with the version bump (databases that
 * auto-commit DDL, like MariaDB, are still protected by the {@code IF NOT EXISTS} in every statement).
 * Refuses to touch a database written by a newer plugin. Plain JDBC, no Bukkit.
 */
public final class SchemaMigrator {

    private SchemaMigrator() {
    }

    /** Runs every pending migration and returns the resulting version. */
    public static int migrate(Connection c, String prefix, List<List<String>> migrations) throws SQLException, StorageException {
        try (Statement st = c.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "schema_version (id INT NOT NULL PRIMARY KEY, version INT NOT NULL)");
        }
        int target = migrations.size();
        int version = version(c, prefix);
        if (version > target) {
            throw new StorageException("Database schema is version " + version + " but this plugin only knows "
                    + target + "; refusing to touch data written by a newer CookieCore");
        }
        for (int v = version; v < target; v++) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                for (String sql : migrations.get(v)) {
                    st.executeUpdate(sql);
                }
                writeVersion(c, prefix, v + 1);
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
        return target;
    }

    /** Current schema version, 0 for a fresh database (the version table must exist). */
    public static int version(Connection c, String prefix) throws SQLException {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT version FROM " + prefix + "schema_version WHERE id = 1")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private static void writeVersion(Connection c, String prefix, int version) throws SQLException {
        try (PreparedStatement up = c.prepareStatement("UPDATE " + prefix + "schema_version SET version = ? WHERE id = 1")) {
            up.setInt(1, version);
            if (up.executeUpdate() > 0) {
                return;
            }
        }
        try (PreparedStatement ins = c.prepareStatement("INSERT INTO " + prefix + "schema_version (id, version) VALUES (1, ?)")) {
            ins.setInt(1, version);
            ins.executeUpdate();
        }
    }
}
