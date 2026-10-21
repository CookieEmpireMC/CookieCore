package dev.casperrs.cookieCore.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;

class SchemaMigratorTest {

    private final H2 db = new H2();

    @Test
    void freshDatabaseEndsAtTheCurrentVersion() throws Exception {
        try (Connection c = db.raw()) {
            int v = SchemaMigrator.migrate(c, "core_", Migrations.all("core_"));
            assertEquals(Migrations.currentVersion(), v);
            assertEquals(1, v, "core-foundation ships schema version 1");
            assertEquals(1, SchemaMigrator.version(c, "core_"));
        }
        assertTrue(db.tableExists("core_players"));
        assertTrue(db.tableExists("core_schema_version"));
    }

    @Test
    void runningTwiceChangesNothingAndKeepsData() throws Exception {
        try (Connection c = db.raw()) {
            SchemaMigrator.migrate(c, "core_", Migrations.all("core_"));
            db.execute("INSERT INTO core_players (uuid, name, first_seen, last_seen) VALUES ('u', 'n', 1, 2)");
            assertEquals(1, SchemaMigrator.migrate(c, "core_", Migrations.all("core_")));
            assertEquals(1, db.count("core_players"));
            assertEquals(1, db.count("core_schema_version"), "still exactly one version row");
        }
    }

    @Test
    void autoCommitIsRestored() throws Exception {
        try (Connection c = db.raw()) {
            SchemaMigrator.migrate(c, "core_", Migrations.all("core_"));
            assertTrue(c.getAutoCommit());
        }
    }

    @Test
    void refusesADatabaseWrittenByANewerPlugin() throws Exception {
        try (Connection c = db.raw()) {
            SchemaMigrator.migrate(c, "core_", Migrations.all("core_"));
            db.execute("UPDATE core_schema_version SET version = 99 WHERE id = 1");
            StorageException e = assertThrows(StorageException.class,
                    () -> SchemaMigrator.migrate(c, "core_", Migrations.all("core_")));
            assertTrue(e.getMessage().contains("99") && e.getMessage().contains("newer"), e.getMessage());
            assertEquals(99, SchemaMigrator.version(c, "core_"), "the newer database is left alone");
        }
    }

    @Test
    void aFailingStatementRollsTheWholeMigrationBack() throws Exception {
        List<List<String>> v1 = List.of(List.of("CREATE TABLE t1 (id INT NOT NULL PRIMARY KEY)"));
        List<List<String>> broken = List.of(v1.get(0),
                List.of("INSERT INTO t1 VALUES (1)", "THIS IS NOT SQL"));
        try (Connection c = db.raw()) {
            assertEquals(1, SchemaMigrator.migrate(c, "x_", v1));
            assertThrows(SQLException.class, () -> SchemaMigrator.migrate(c, "x_", broken));
            assertEquals(0, db.count("t1"), "the insert before the bad statement was rolled back");
            assertEquals(1, SchemaMigrator.version(c, "x_"), "version not bumped");
            assertTrue(c.getAutoCommit(), "auto-commit restored after a failure");
        }
    }

    @Test
    void afterAFailureTheFixedMigrationRuns() throws Exception {
        List<List<String>> v1 = List.of(List.of("CREATE TABLE IF NOT EXISTS t1 (id INT NOT NULL PRIMARY KEY)"));
        List<List<String>> broken = List.of(v1.get(0), List.of("NOPE"));
        List<List<String>> fixed = List.of(v1.get(0), List.of("INSERT INTO t1 VALUES (7)"));
        try (Connection c = db.raw()) {
            assertThrows(SQLException.class, () -> SchemaMigrator.migrate(c, "x_", broken));
            assertEquals(1, SchemaMigrator.version(c, "x_"));
            assertEquals(2, SchemaMigrator.migrate(c, "x_", fixed));
            assertEquals(1, db.count("t1"));
        }
    }

    @Test
    void migrationsApplyInOrderFromAnOlderVersion() throws Exception {
        List<List<String>> v1 = List.of(List.of("CREATE TABLE a (id INT)"));
        List<List<String>> v2 = List.of(v1.get(0), List.of("CREATE TABLE b (id INT)"));
        try (Connection c = db.raw()) {
            SchemaMigrator.migrate(c, "p_", v1);
            assertFalse(db.tableExists("b"));
            assertEquals(2, SchemaMigrator.migrate(c, "p_", v2));
            assertTrue(db.tableExists("b"));
        }
    }

    @Test
    void currentVersionMatchesTheMigrationListAndIsPrefixIndependent() {
        assertEquals(Migrations.all("core_").size(), Migrations.currentVersion());
        assertEquals(Migrations.all("a_").size(), Migrations.all("").size());
    }

    @Test
    void everyShippedStatementIsIdempotent() {
        // MariaDB auto-commits DDL, so a half-applied migration must be re-runnable
        for (List<String> migration : Migrations.all("core_")) {
            for (String sql : migration) {
                assertTrue(sql.toUpperCase().contains("IF NOT EXISTS") || !sql.toUpperCase().startsWith("CREATE"),
                        "CREATE without IF NOT EXISTS: " + sql);
            }
        }
    }
}
