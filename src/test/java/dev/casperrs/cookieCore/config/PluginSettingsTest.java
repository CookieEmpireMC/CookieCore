package dev.casperrs.cookieCore.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.casperrs.cookieCore.TestSupport;
import dev.casperrs.cookieCore.storage.DatabaseSettings;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class PluginSettingsTest {

    private static final Path DATA = Path.of("plugins", "CookieCore").toAbsolutePath();
    private final List<String> problems = new ArrayList<>();

    private PluginSettings from(String yaml) {
        return PluginSettings.from(TestSupport.yaml(yaml), DATA, problems::add);
    }

    @Test
    void emptyConfigGivesDefaultsWithoutProblems() {
        PluginSettings s = PluginSettings.from(new YamlConfiguration(), DATA, problems::add);
        assertEquals("Cookie Empire", s.serverName());
        assertEquals(DatabaseSettings.Type.H2, s.database().type());
        assertEquals("core_", s.database().tablePrefix());
        assertEquals(3306, s.database().port());
        assertEquals(10, s.storageTimeoutSeconds());
        assertEquals(DATA.resolve("data/core"), s.database().h2File());
        assertTrue(problems.isEmpty(), problems.toString());
    }

    @Test
    void shippedConfigYmlHasNoProblems() {
        PluginSettings s = PluginSettings.from(TestSupport.resourceYaml("config.yml"), DATA, problems::add);
        assertTrue(problems.isEmpty(), problems.toString());
        assertEquals("Cookie Empire", s.serverName());
        assertEquals(DatabaseSettings.Type.H2, s.database().type());
    }

    @Test
    void validValuesAreRead() {
        PluginSettings s = from("""
                server:
                  name: "My Server"
                database:
                  type: MariaDB
                  table-prefix: my_
                  shutdown-timeout-seconds: 30
                  file: other/file
                  host: db.example
                  port: 3307
                  name: cc
                  user: u
                  password: p
                """);
        assertTrue(problems.isEmpty(), problems.toString());
        assertEquals("My Server", s.serverName());
        DatabaseSettings db = s.database();
        assertEquals(DatabaseSettings.Type.MARIADB, db.type());
        assertEquals("my_", db.tablePrefix());
        assertEquals(30, s.storageTimeoutSeconds());
        assertEquals(DATA.resolve("other/file"), db.h2File());
        assertEquals("jdbc:mariadb://db.example:3307/cc", db.jdbcUrl());
        assertEquals("u", db.username());
        assertEquals("p", db.password());
    }

    @Test
    void blankServerNameReportsAndUsesDefault() {
        PluginSettings s = from("server:\n  name: \"   \"\n");
        assertEquals("Cookie Empire", s.serverName());
        assertEquals(1, problems.size());
        assertTrue(problems.getFirst().contains("server.name"));
    }

    @Test
    void unknownDatabaseTypeReportsAndUsesH2() {
        PluginSettings s = from("database:\n  type: oracle\n");
        assertEquals(DatabaseSettings.Type.H2, s.database().type());
        assertEquals(1, problems.size());
        assertTrue(problems.getFirst().contains("database.type"));
    }

    @Test
    void mysqlIsAnAliasForMariaDb() {
        assertEquals(DatabaseSettings.Type.MARIADB, from("database:\n  type: mysql\n").database().type());
        assertTrue(problems.isEmpty());
    }

    @Test
    void badTablePrefixReportsAndUsesDefault() {
        for (String bad : List.of("bad prefix", "semi;colon", "drop--", "a".repeat(33), "naïve")) {
            problems.clear();
            PluginSettings s = from("database:\n  table-prefix: \"" + bad + "\"\n");
            assertEquals("core_", s.database().tablePrefix(), "prefix '" + bad + "'");
            assertEquals(1, problems.size(), "prefix '" + bad + "'");
            assertTrue(problems.getFirst().contains("table-prefix"));
        }
    }

    @Test
    void emptyAndMaxLengthPrefixAreAllowed() {
        assertEquals("", from("database:\n  table-prefix: \"\"\n").database().tablePrefix());
        assertEquals("a".repeat(32), from("database:\n  table-prefix: \"" + "a".repeat(32) + "\"\n").database().tablePrefix());
        assertTrue(problems.isEmpty());
    }

    @Test
    void outOfRangePortReportsAndUses3306() {
        for (int bad : new int[] {0, -1, 65536, 99999}) {
            problems.clear();
            PluginSettings s = from("database:\n  port: " + bad + "\n");
            assertEquals(3306, s.database().port(), "port " + bad);
            assertEquals(1, problems.size(), "port " + bad);
            assertTrue(problems.getFirst().contains("database.port"));
        }
        problems.clear();
        assertEquals(65535, from("database:\n  port: 65535\n").database().port());
        assertEquals(1, from("database:\n  port: 1\n").database().port());
        assertTrue(problems.isEmpty());
    }

    @Test
    void nonNumericPortFallsBackToDefault() {
        // YamlConfiguration#getInt hands back the default for a non-number, so no problem is reported (see review F-09)
        assertEquals(3306, from("database:\n  port: abc\n").database().port());
    }

    @Test
    void badShutdownTimeoutReportsAndUsesTen() {
        for (int bad : new int[] {0, -5}) {
            problems.clear();
            assertEquals(10, from("database:\n  shutdown-timeout-seconds: " + bad + "\n").storageTimeoutSeconds());
            assertEquals(1, problems.size());
            assertTrue(problems.getFirst().contains("shutdown-timeout-seconds"));
        }
    }

    @Test
    void severalProblemsAreAllReported() {
        PluginSettings s = from("""
                server:
                  name: ""
                database:
                  type: nope
                  table-prefix: "x y"
                  port: 0
                  shutdown-timeout-seconds: 0
                """);
        assertEquals(5, problems.size(), problems.toString());
        assertEquals(DatabaseSettings.Type.H2, s.database().type());
    }

    @Test
    void withDatabaseReplacesOnlyTheDatabase() {
        PluginSettings a = from("server:\n  name: A\ndatabase:\n  shutdown-timeout-seconds: 7\n");
        PluginSettings b = from("database:\n  type: mariadb\n  host: other\n");
        PluginSettings merged = b.withDatabase(a.database());
        assertEquals(b.serverName(), merged.serverName());
        assertEquals(b.storageTimeoutSeconds(), merged.storageTimeoutSeconds());
        assertSame(a.database(), merged.database());
    }

    @Test
    void passwordNeverAppearsInDescribe() {
        PluginSettings s = from("database:\n  type: mariadb\n  password: s3cret\n");
        assertTrue(!s.database().describe().contains("s3cret"));
    }

    @Test
    @Disabled("F-07: DatabaseSettings / PluginSettings are records, so toString() prints the database password; any future log of the settings would leak it")
    void toStringNeverContainsThePassword() {
        PluginSettings s = from("database:\n  type: mariadb\n  password: s3cret\n");
        assertTrue(!s.toString().contains("s3cret"), s.toString());
        assertTrue(!s.database().toString().contains("s3cret"), s.database().toString());
    }
}
