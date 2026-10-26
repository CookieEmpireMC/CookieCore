package dev.casperrs.cookieCore.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.casperrs.cookieCore.TestSupport;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import org.junit.jupiter.api.Test;

class JdbcPlayerStoreTest {

    private final H2 db = new H2();
    private final TestSupport.LogCapture log = new TestSupport.LogCapture();
    private final JdbcPlayerStore store = new JdbcPlayerStore(db.factory(), "core_", log.logger);
    private final UUID a = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private final UUID b = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    @Test
    void freshDatabaseIsMigratedAndASecondInitKeepsTheData() throws Exception {
        store.init();
        assertEquals(1, store.schemaVersion());
        assertTrue(db.tableExists("core_players"));
        store.upsertSeen(a, "Alice", 100);
        store.init();
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
}
