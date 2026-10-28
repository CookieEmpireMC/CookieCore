package dev.casperrs.cookieCore.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static dev.casperrs.cookieCore.storage.JdbcSession.limit;

/**
 * JDBC {@link PunishmentStore} for H2 and MariaDB. The table is created by the migrations that
 * {@link JdbcPlayerStore#init()} runs, which must have finished before the first call here.
 */
public final class JdbcPunishmentStore implements PunishmentStore {

    private final JdbcSession session;
    private final String p;
    private final Logger log;

    /** Shares the connection of {@code players}, so closing that store closes this one too. */
    public JdbcPunishmentStore(JdbcPlayerStore players, Logger log) {
        this.session = players.session();
        this.p = players.tablePrefix();
        this.log = log;
    }

    @Override
    public long insert(NewPunishment row) throws StorageException {
        try {
            Connection c = session.connection();
            // the key is read by index: H2 and MariaDB name the generated column differently
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO " + p + "punishments (type, target_uuid, target_name, "
                    + "actor_uuid, actor_name, reason, created_at, expires_at, silent, active) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, row.type().name());
                ps.setString(2, row.targetUuid().toString());
                ps.setString(3, limit(row.targetName(), JdbcPlayerStore.MAX_NAME_LENGTH));
                if (row.actorUuid() == null) {
                    ps.setNull(4, Types.VARCHAR);
                } else {
                    ps.setString(4, row.actorUuid().toString());
                }
                ps.setString(5, limit(row.actorName(), JdbcPlayerStore.MAX_NAME_LENGTH));
                ps.setString(6, limit(row.reason(), JdbcPlayerStore.MAX_REASON_LENGTH));
                ps.setLong(7, row.createdAt());
                if (row.expiresAt() == null) {
                    ps.setNull(8, Types.BIGINT);
                } else {
                    ps.setLong(8, row.expiresAt());
                }
                ps.setBoolean(9, row.silent());
                ps.setBoolean(10, row.type().activeOnInsert());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new SQLException("the database returned no generated id");
                    }
                    return keys.getLong(1);
                }
            }
        } catch (SQLException e) {
            session.invalidate();
            throw new StorageException("Could not record the " + row.type() + " of " + row.targetName(), e);
        }
    }

    @Override
    public List<BanRecord> loadActiveBans(long now) throws StorageException {
        return loadActive(PunishmentType.BAN, now);
    }

    @Override
    public List<MuteRecord> loadActiveMutes(long now) throws StorageException {
        return loadActive(PunishmentType.MUTE, now).stream().map(MuteRecord::of).toList();
    }

    private List<BanRecord> loadActive(PunishmentType type, long now) throws StorageException {
        List<BanRecord> out = new ArrayList<>();
        try {
            Connection c = session.connection();
            try (PreparedStatement st = c.prepareStatement("SELECT id, target_uuid, target_name, actor_name, reason, created_at, "
                    + "expires_at FROM " + p + "punishments WHERE type = '" + type.name() + "' AND active = TRUE "
                    + "AND (expires_at IS NULL OR expires_at > ?)")) {
                st.setLong(1, now);
                try (ResultSet rs = st.executeQuery()) {
                    int skipped = 0;
                    while (rs.next()) {
                        try {
                            long expires = rs.getLong(7);
                            Long expiresAt = rs.wasNull() ? null : expires; // wasNull() refers to the last column read
                            out.add(new BanRecord(rs.getLong(1), UUID.fromString(rs.getString(2)), rs.getString(3),
                                    rs.getString(4), rs.getString(5), rs.getLong(6), expiresAt));
                        } catch (IllegalArgumentException corrupt) {
                            skipped++; // a damaged row must not lift every other ban
                        }
                    }
                    if (skipped > 0) {
                        log.warning("Skipped " + skipped + " row(s) in " + p + "punishments with an invalid uuid");
                    }
                }
            }
        } catch (SQLException e) {
            session.invalidate();
            throw new StorageException("Could not load the active " + type + "s", e);
        }
        return out;
    }

    @Override
    public int deactivateBans(UUID target, UUID byUuid, String byName, String reason, long now) throws StorageException {
        return deactivate(PunishmentType.BAN, target, byUuid, byName, reason, now);
    }

    @Override
    public int deactivateMutes(UUID target, UUID byUuid, String byName, String reason, long now) throws StorageException {
        return deactivate(PunishmentType.MUTE, target, byUuid, byName, reason, now);
    }

    private int deactivate(PunishmentType type, UUID target, UUID byUuid, String byName, String reason, long now) throws StorageException {
        try {
            Connection c = session.connection();
            try (PreparedStatement ps = c.prepareStatement("UPDATE " + p + "punishments SET active = FALSE, removed_at = ?, "
                    + "removed_by_uuid = ?, removed_by_name = ?, remove_reason = ? "
                    + "WHERE target_uuid = ? AND type = '" + type.name() + "' AND active = TRUE")) {
                ps.setLong(1, now);
                if (byUuid == null) {
                    ps.setNull(2, Types.VARCHAR);
                } else {
                    ps.setString(2, byUuid.toString());
                }
                ps.setString(3, limit(byName, JdbcPlayerStore.MAX_NAME_LENGTH));
                ps.setString(4, limit(reason, JdbcPlayerStore.MAX_REASON_LENGTH));
                ps.setString(5, target.toString());
                return ps.executeUpdate();
            }
        } catch (SQLException e) {
            session.invalidate();
            throw new StorageException("Could not remove the " + type + " of " + target, e);
        }
    }
}
