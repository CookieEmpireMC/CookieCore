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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class JdbcPlayerStoreTest {

    private final H2 db = new H2();
    private final JdbcPlayerStore store = new JdbcPlayerStore(db.factory(), "core_");
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
    @Disabled("F-04: a corrupt uuid row makes loadAll throw IllegalArgumentException (not StorageException); PlayerDirectory.start then loses the whole warm-up and the error is swallowed by the executor")
    void aCorruptRowIsReportedAsStorageExceptionOrSkipped() throws Exception {
        store.init();
        db.execute("INSERT INTO core_players (uuid, name, first_seen, last_seen) VALUES ('not-a-uuid', 'Bad', 1, 1)");
        // either contract is fine: a StorageException, or the bad row skipped; an unchecked exception is not
        try {
            store.loadAll();
        } catch (StorageException expected) {
            return;
        }
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
