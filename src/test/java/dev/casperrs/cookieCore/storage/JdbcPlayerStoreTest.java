package dev.casperrs.cookieCore.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JdbcPlayerStoreTest {

    private final H2 db = new H2();
    private final dev.casperrs.cookieCore.TestSupport.LogCapture log = new dev.casperrs.cookieCore.TestSupport.LogCapture();
    private final JdbcPlayerStore store = new JdbcPlayerStore(db.factory(), "core_", log.logger);
    private final UUID a = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private final UUID b = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    @Test
    void freshDatabaseIsMigratedToVersionOne() throws Exception {
        store.init();
        assertEquals(1, store.schemaVersion());
        assertTrue(db.tableExists("core_players"));
    }

    @Test
    void initTwiceKeepsTheData() throws Exception {
        store.init();
        store.upsertSeen(a, "Alice", 100);
        store.init();
        assertEquals(1, store.schemaVersion());
        assertEquals(1, store.loadAll().size());
    }

    @Test
    void initRefusesANewerSchema() throws Exception {
        store.init();
        db.execute("UPDATE core_schema_version SET version = 2 WHERE id = 1");
        StorageException e = assertThrows(StorageException.class, store::init);
        assertTrue(e.getMessage().contains("newer"), e.getMessage());
    }

    @Test
    void insertSetsFirstAndLastSeenToNow() throws Exception {
        store.init();
        store.upsertSeen(a, "Alice", 1000);
        PlayerRecord r = store.loadAll().getFirst();
        assertEquals(new PlayerRecord(a, "Alice", 1000, 1000), r);
    }

    @Test
    void updateKeepsFirstSeenAndChangesNameAndLastSeen() throws Exception {
        store.init();
        store.upsertSeen(a, "Alice", 1000);
        store.upsertSeen(a, "Alicia", 5000);
        List<PlayerRecord> all = store.loadAll();
        assertEquals(1, all.size(), "upsert, not a second row");
        assertEquals(new PlayerRecord(a, "Alicia", 1000, 5000), all.getFirst());
    }

    @Test
    void longNamesAreTruncatedToTheColumn() throws Exception {
        store.init();
        String name = "x".repeat(40);
        store.upsertSeen(a, name, 1);
        assertEquals("x".repeat(JdbcPlayerStore.MAX_NAME_LENGTH), store.loadAll().getFirst().name());
    }

    @Test
    void loadAllIsOrderedByLastSeenAscending() throws Exception {
        store.init();
        store.upsertSeen(a, "A", 300);
        store.upsertSeen(b, "B", 100);
        assertEquals(List.of(b, a), store.loadAll().stream().map(PlayerRecord::uuid).toList());
    }

    @Test
    void findByNameIsCaseInsensitiveAndPicksTheMostRecentOwner() throws Exception {
        store.init();
        store.upsertSeen(a, "Steve", 100);
        store.upsertSeen(b, "STEVE", 200);
        Optional<PlayerRecord> found = store.findByName("steve");
        assertTrue(found.isPresent());
        assertEquals(b, found.get().uuid());
        assertEquals(Optional.empty(), store.findByName("nobody"));
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
    void namesWithQuotesAreStoredLiterally() throws Exception {
        store.init();
        store.upsertSeen(a, "O'Brien\"; --", 1);
        assertEquals("O'Brien\"; --", store.loadAll().getFirst().name());
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
    void closeIsIdempotent() {
        store.close();
        store.close();
    }

    @Test
    void tablePrefixIsApplied() throws Exception {
        JdbcPlayerStore other = new JdbcPlayerStore(db.factory(), "zz_");
        other.init();
        assertTrue(db.tableExists("zz_players"));
        assertFalse(db.tableExists("core_players"));
    }

    @Test
    void badPrefixIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new JdbcPlayerStore(db.factory(), "a b"));
        assertThrows(IllegalArgumentException.class, () -> new JdbcPlayerStore(db.factory(), "x;DROP"));
        assertThrows(IllegalArgumentException.class, () -> new DatabaseSettings(DatabaseSettings.Type.H2, java.nio.file.Path.of("x"),
                "h", 1, "d", "u", "p", "bad prefix"));
    }

    @Test
    void connectionFailureBecomesAStorageException() {
        JdbcPlayerStore broken = new JdbcPlayerStore(() -> {
            throw new SQLException("down");
        }, "core_");
        assertThrows(StorageException.class, broken::init);
        assertThrows(StorageException.class, () -> broken.upsertSeen(a, "A", 1));
        assertThrows(StorageException.class, broken::loadAll);
        assertThrows(StorageException.class, () -> broken.findByName("A"));
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

    @Test
    void aCorruptUuidRowIsSkippedAndTheOtherPlayersStillLoad() throws Exception {
        store.init();
        store.upsertSeen(a, "Alice", 100);
        db.execute("INSERT INTO core_players (uuid, name, first_seen, last_seen) VALUES ('not-a-uuid', 'Bad', 1, 150)");
        store.upsertSeen(b, "Bob", 200);

        List<PlayerRecord> all = store.loadAll();

        assertEquals(List.of(a, b), all.stream().map(PlayerRecord::uuid).toList());
        assertEquals(1, log.records.size(), "one warning for the skipped rows, not one per row");
        assertEquals(java.util.logging.Level.WARNING, log.records.getFirst().getLevel());
        assertTrue(log.records.getFirst().getMessage().contains("Skipped 1 row(s) in core_players"), log.records.getFirst().getMessage());
    }

    @Test
    void severalCorruptRowsAreCountedInOneWarning() throws Exception {
        store.init();
        db.execute("INSERT INTO core_players (uuid, name, first_seen, last_seen) VALUES ('x1', 'B1', 1, 1)");
        db.execute("INSERT INTO core_players (uuid, name, first_seen, last_seen) VALUES ('', 'B2', 1, 2)");
        db.execute("INSERT INTO core_players (uuid, name, first_seen, last_seen) VALUES ('12345', 'B3', 1, 3)");
        store.upsertSeen(a, "Alice", 100);

        assertEquals(1, store.loadAll().size());
        assertEquals(1, log.records.size());
        assertTrue(log.records.getFirst().getMessage().contains("Skipped 3 row(s)"), log.records.getFirst().getMessage());
    }

    @Test
    void aCleanTableLogsNothing() throws Exception {
        store.init();
        store.upsertSeen(a, "Alice", 100);
        store.loadAll();
        assertTrue(log.records.isEmpty(), log.records.toString());
    }

    @Test
    void lookingUpACorruptRowFailsAsStorageExceptionNamingThePlayer() throws Exception {
        store.init();
        db.execute("INSERT INTO core_players (uuid, name, first_seen, last_seen) VALUES ('not-a-uuid', 'Bad', 1, 1)");
        StorageException e = assertThrows(StorageException.class, () -> store.findByName("bad"));
        assertTrue(e.getMessage().contains("Bad") || e.getMessage().contains("bad"), e.getMessage());
        assertTrue(e.getCause() instanceof IllegalArgumentException);
        // the connection stays usable
        store.upsertSeen(a, "Alice", 100);
        assertEquals("Alice", store.findByName("alice").orElseThrow().name());
    }

    @Test
    void theTwoArgumentConstructorStillWorksWithTheDefaultLogger() throws Exception {
        JdbcPlayerStore plain = new JdbcPlayerStore(db.factory(), "core_");
        plain.init();
        db.execute("INSERT INTO core_players (uuid, name, first_seen, last_seen) VALUES ('nope', 'Bad', 1, 1)");
        assertEquals(0, plain.loadAll().size());
    }

    @Test
    void connectionsAreOpenedLazilyAndReused() throws Exception {
        assertEquals(0, db.opened);
        store.init();
        store.upsertSeen(a, "A", 1);
        store.loadAll();
        store.findByName("a");
        assertEquals(1, db.opened, "one connection for the whole session");
        try (Connection ignored = db.raw()) {
            assertEquals(1, db.opened);
        }
    }
}
