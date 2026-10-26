package dev.casperrs.cookieCore.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;

class SchemaMigratorTest {

    private final H2 db = new H2();

    @Test
    void freshDatabaseEndsAtTheCurrentVersionAndRunningAgainChangesNothing() throws Exception {
        try (Connection c = db.raw()) {
            assertEquals(Migrations.currentVersion(), SchemaMigrator.migrate(c, "core_", Migrations.all("core_")));
            db.execute("INSERT INTO core_players (uuid, name, first_seen, last_seen) VALUES ('u', 'n', 1, 2)");
            assertEquals(Migrations.currentVersion(), SchemaMigrator.migrate(c, "core_", Migrations.all("core_")));
            assertEquals(1, db.count("core_players"), "data kept");
            assertEquals(1, db.count("core_schema_version"), "still exactly one version row");
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

    /** A failed migration rolls back its own inserts, keeps the old version, and restores auto-commit. */
    @Test
    void aFailingStatementRollsTheWholeMigrationBack() throws Exception {
        List<List<String>> v1 = List.of(List.of("CREATE TABLE t1 (id INT NOT NULL PRIMARY KEY)"));
        List<List<String>> broken = List.of(v1.get(0), List.of("INSERT INTO t1 VALUES (1)", "THIS IS NOT SQL"));
        try (Connection c = db.raw()) {
            assertEquals(1, SchemaMigrator.migrate(c, "x_", v1));
            assertThrows(SQLException.class, () -> SchemaMigrator.migrate(c, "x_", broken));
            assertEquals(0, db.count("t1"), "the insert before the bad statement was rolled back");
            assertEquals(1, SchemaMigrator.version(c, "x_"), "version not bumped");
            assertTrue(c.getAutoCommit(), "auto-commit restored after a failure");
        }
    }

    /** MariaDB auto-commits DDL, so a half-applied migration must be re-runnable. */
    @Test
    void everyShippedCreateStatementIsIdempotent() {
        for (List<String> migration : Migrations.all("core_")) {
            for (String sql : migration) {
                assertTrue(sql.toUpperCase().contains("IF NOT EXISTS") || !sql.toUpperCase().startsWith("CREATE"),
                        "CREATE without IF NOT EXISTS: " + sql);
            }
        }
    }
}
