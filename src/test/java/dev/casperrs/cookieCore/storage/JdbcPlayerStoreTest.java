package dev.casperrs.cookieCore.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.casperrs.cookieCore.TestSupport;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import org.junit.jupiter.api.Test;

class JdbcPlayerStoreTest {

    private final H2 db = new H2();
    private final TestSupport.LogCapture log = new TestSupport.LogCapture();
    private final JdbcPlayerStore store = new JdbcPlayerStore(db.factory(), "core_", log.logger);
    private final JdbcPunishmentStore punishments = new JdbcPunishmentStore(store, log.logger);
    private final UUID a = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private final UUID b = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    @Test
    void freshDatabaseIsMigratedAndASecondInitKeepsTheData() throws Exception {
        store.init();
        assertEquals(Migrations.currentVersion(), store.schemaVersion());
        assertTrue(db.tableExists("core_players"));
        assertTrue(db.tableExists("core_sidebar_prefs"));
        store.upsertSeen(a, "Alice", 100);
        store.init();
        assertEquals(1, store.loadAll().size());
    }

    @Test
    void initRefusesANewerSchema() throws Exception {
        store.init();
        db.execute("UPDATE core_schema_version SET version = " + (Migrations.currentVersion() + 1) + " WHERE id = 1");
        StorageException e = assertThrows(StorageException.class, store::init);
        assertTrue(e.getMessage().contains("newer"), e.getMessage());
    }

    @Test
    void updateKeepsFirstSeenAndChangesNameAndLastSeen() throws Exception {
        store.init();
        store.upsertSeen(a, "Alice", 1000);
        store.upsertSeen(a, "Alicia", 5000);
        List<PlayerRecord> all = store.loadAll();
        assertEquals(List.of(new PlayerRecord(a, "Alicia", 1000, 5000)), all, "upsert, not a second row");
    }

    @Test
    void longNamesAreTruncatedToTheColumn() throws Exception {
        store.init();
        store.upsertSeen(a, "x".repeat(40), 1);
        assertEquals("x".repeat(JdbcPlayerStore.MAX_NAME_LENGTH), store.loadAll().getFirst().name());
    }

    @Test
    void findByNameIsCaseInsensitiveAndPicksTheMostRecentOwner() throws Exception {
        store.init();
        store.upsertSeen(a, "Steve", 100);
        store.upsertSeen(b, "STEVE", 200);
        Optional<PlayerRecord> found = store.findByName("steve");
        assertTrue(found.isPresent());
        assertEquals(b, found.get().uuid());
    }

    @Test
    void findByNameIsNotAPatternAndNotInjectable() throws Exception {
        store.init();
        store.upsertSeen(a, "Alice", 1);
        assertTrue(store.findByName("%").isEmpty());
        assertTrue(store.findByName("Ali_e").isEmpty());
        assertTrue(store.findByName("x' OR '1'='1").isEmpty());
        assertTrue(store.findByName("'; DROP TABLE core_players; --").isEmpty());
        assertTrue(db.tableExists("core_players"));
        assertEquals(1, db.count("core_players"));
    }

    @Test
    void reconnectsAfterTheConnectionWasClosed() throws Exception {
        store.init();
        store.upsertSeen(a, "A", 1);
        int before = db.opened;
        store.close();
        store.upsertSeen(b, "B", 2);
        assertTrue(db.opened > before, "a new connection was opened");
        assertEquals(2, store.loadAll().size());
    }

    @Test
    void tablePrefixIsAppliedAndAnUnsafePrefixIsRejected() throws Exception {
        JdbcPlayerStore other = new JdbcPlayerStore(db.factory(), "zz_");
        other.init();
        assertTrue(db.tableExists("zz_players"));
        assertFalse(db.tableExists("core_players"));
        assertThrows(IllegalArgumentException.class, () -> new JdbcPlayerStore(db.factory(), "x;DROP"));
    }

    @Test
    void connectionFailureBecomesAStorageException() {
        JdbcPlayerStore broken = new JdbcPlayerStore(() -> {
            throw new SQLException("down");
        }, "core_");
        assertThrows(StorageException.class, broken::init);
        assertThrows(StorageException.class, () -> broken.upsertSeen(a, "A", 1));
        assertThrows(StorageException.class, broken::loadAll);
    }

    @Test
    void aMissingTableBecomesAStorageExceptionAndTheStoreRecovers() throws Exception {
        store.init();
        db.execute("DROP TABLE core_players");
        assertThrows(StorageException.class, store::loadAll);
        db.execute("CREATE TABLE core_players (uuid VARCHAR(36) NOT NULL PRIMARY KEY, name VARCHAR(32) NOT NULL, first_seen BIGINT NOT NULL, last_seen BIGINT NOT NULL)");
        store.upsertSeen(a, "A", 1);
        assertEquals(1, store.loadAll().size());
    }

    /** F-04: a corrupt uuid row is skipped with one warning for all skipped rows; the other players still load. */
    @Test
    void corruptRowsAreSkippedWithOneWarningAndTheOthersLoad() throws Exception {
        store.init();
        store.upsertSeen(a, "Alice", 100);
        db.execute("INSERT INTO core_players (uuid, name, first_seen, last_seen) VALUES ('not-a-uuid', 'Bad', 1, 150)");
        db.execute("INSERT INTO core_players (uuid, name, first_seen, last_seen) VALUES ('', 'B2', 1, 2)");
        store.upsertSeen(b, "Bob", 200);

        assertEquals(List.of(a, b), store.loadAll().stream().map(PlayerRecord::uuid).toList());
        assertEquals(1, log.records.size(), "one warning for the skipped rows, not one per row");
        assertEquals(Level.WARNING, log.records.getFirst().getLevel());
        assertTrue(log.records.getFirst().getMessage().contains("Skipped 2 row(s) in core_players"), log.records.getFirst().getMessage());
    }

    /** Sidebar choice: visible by default, a hidden choice survives a restart, and changing it updates the one row. */
    @Test
    void sidebarChoiceDefaultsToVisibleAndTheHiddenChoiceSurvivesAReopen() throws Exception {
        store.init();
        assertTrue(store.loadVisible(a), "no row means visible");
        store.saveVisible(a, false);
        JdbcPlayerStore restarted = new JdbcPlayerStore(db.factory(), "core_", log.logger);
        restarted.init();
        assertFalse(restarted.loadVisible(a));
        restarted.saveVisible(a, true);
        assertTrue(restarted.loadVisible(a));
        assertEquals(1, db.count("core_sidebar_prefs"), "update, not a second row");
    }

    /** A database written by v1 (core-foundation) gains the sidebar table on start and keeps every player. */
    @Test
    void aVersionOneDatabaseMigratesToTheCurrentVersionWithoutLosingPlayers() throws Exception {
        try (java.sql.Connection c = db.raw()) {
            SchemaMigrator.migrate(c, "core_", Migrations.all("core_").subList(0, 1));
        }
        db.execute("INSERT INTO core_players (uuid, name, first_seen, last_seen) VALUES ('" + a + "', 'Alice', 1, 2)");
        assertFalse(db.tableExists("core_sidebar_prefs"));
        store.init();
        assertEquals(Migrations.currentVersion(), store.schemaVersion());
        assertTrue(db.tableExists("core_sidebar_prefs"));
        assertEquals(List.of(a), store.loadAll().stream().map(PlayerRecord::uuid).toList());
        assertTrue(store.loadVisible(a));
    }

    /** Each kick, ban and tempban is one row with its generated id; kicks are stored inactive, so only bans load. */
    @Test
    void punishmentRowsGetGeneratedIdsKicksStayInactiveAndOnlyBansLoad() throws Exception {
        store.init();
        UUID staff = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        long kick = punishments.insert(new NewPunishment(PunishmentType.KICK, a, "Alice", null, "Console", "spam", 100, null, true));
        long ban = punishments.insert(new NewPunishment(PunishmentType.BAN, b, "Bob", staff, "Alex", "griefing", 200, null, false));
        long tempban = punishments.insert(new NewPunishment(PunishmentType.BAN, c, "Carl", staff, "Alex", "spam", 300, 5_000L, false));
        assertTrue(kick < ban && ban < tempban, "ids are generated in order");
        List<BanRecord> active = punishments.loadActiveBans(1_000);
        assertEquals(List.of(ban, tempban), active.stream().map(BanRecord::id).toList());
        assertNull(active.getFirst().expiresAt());
        assertEquals(Long.valueOf(5_000L), active.getLast().expiresAt());
        assertEquals(2, db.count("core_punishments WHERE active = TRUE"), "the kick row is inactive");
    }

    /** An unban marks every active row of the player, including an expired tempban; other players are untouched. */
    @Test
    void unbanDeactivatesEveryActiveRowOfThePlayerAndRecordsWhoDidIt() throws Exception {
        store.init();
        punishments.insert(new NewPunishment(PunishmentType.BAN, a, "Alice", null, "Console", "old", 100, 500L, false));
        punishments.insert(new NewPunishment(PunishmentType.BAN, a, "Alice", null, "Console", "perm", 200, null, false));
        punishments.insert(new NewPunishment(PunishmentType.BAN, b, "Bob", null, "Console", "perm", 200, null, false));
        assertEquals(2, punishments.deactivateBans(a, UUID.randomUUID(), "Alex", "appealed", 1_000));
        assertEquals(List.of(b), punishments.loadActiveBans(1_000).stream().map(BanRecord::targetUuid).toList());
        assertEquals(2, db.count("core_punishments WHERE removed_by_name = 'Alex' AND removed_at = 1000"));
    }

    /** A damaged punishment row is skipped with one warning; the other bans still load. */
    @Test
    void aCorruptPunishmentRowIsSkippedAndTheOthersLoad() throws Exception {
        store.init();
        punishments.insert(new NewPunishment(PunishmentType.BAN, b, "Bob", null, "Console", "ok", 1, null, false));
        db.execute("INSERT INTO core_punishments (type, target_uuid, target_name, actor_uuid, actor_name, reason, created_at, "
                + "expires_at, silent, active) VALUES ('BAN', 'not-a-uuid', 'Bad', NULL, 'Console', 'x', 1, NULL, FALSE, TRUE)");
        assertEquals(List.of(b), punishments.loadActiveBans(0).stream().map(BanRecord::targetUuid).toList());
        assertTrue(log.messages().stream().anyMatch(m -> m.contains("Skipped 1 row(s) in core_punishments")), log.messages().toString());
    }

    /** Mutes and bans load and undo separately: an expired mute is not loaded, and undoing a mute keeps the ban. */
    @Test
    void mutesLoadAndUndoSeparatelyFromBans() throws Exception {
        store.init();
        punishments.insert(new NewPunishment(PunishmentType.BAN, a, "Alice", null, "Console", "ban", 100, null, false));
        punishments.insert(new NewPunishment(PunishmentType.MUTE, a, "Alice", null, "Console", "expired", 100, 500L, false));
        punishments.insert(new NewPunishment(PunishmentType.MUTE, a, "Alice", null, "Console", "perm", 200, null, false));
        punishments.insert(new NewPunishment(PunishmentType.MUTE, b, "Bob", null, "Console", "perm", 200, null, false));
        List<MuteRecord> mutes = punishments.loadActiveMutes(1_000);
        assertEquals(2, mutes.size());
        assertEquals(Set.of(a, b), Set.copyOf(mutes.stream().map(MuteRecord::targetUuid).toList()));

        assertEquals(2, punishments.deactivateMutes(a, UUID.randomUUID(), "Alex", "appealed", 1_000), "the expired row counts too, like an unban");
        assertEquals(List.of(b), punishments.loadActiveMutes(1_000).stream().map(MuteRecord::targetUuid).toList());
        assertEquals(List.of(a), punishments.loadActiveBans(1_000).stream().map(BanRecord::targetUuid).toList());
    }
}
