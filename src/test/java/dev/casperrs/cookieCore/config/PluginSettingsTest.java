package dev.casperrs.cookieCore.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.casperrs.cookieCore.TestSupport;
import dev.casperrs.cookieCore.storage.DatabaseSettings;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
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
        assertEquals(DATA.resolve("data/core"), s.database().h2File());
        assertTrue(problems.isEmpty(), problems.toString());
    }

    @Test
    void shippedConfigYmlLoadsWithoutProblems() {
        PluginSettings s = PluginSettings.from(TestSupport.resourceYaml("config.yml"), DATA, problems::add);
        assertTrue(problems.isEmpty(), problems.toString());
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
        assertEquals(DATA.resolve("other/file"), db.h2File());
        assertEquals("jdbc:mariadb://db.example:3307/cc", db.jdbcUrl());
        assertEquals("u", db.username());
        assertEquals("p", db.password());
    }

    @Test
    void invalidStringsReportOnceAndUseTheDefault() {
        PluginSettings blankName = from("server:\n  name: \"   \"\n");
        assertEquals("Cookie Empire", blankName.serverName());
        assertEquals(1, problems.size());

        problems.clear();
        assertEquals(DatabaseSettings.Type.H2, from("database:\n  type: oracle\n").database().type());
        assertEquals(1, problems.size());
        assertTrue(problems.getFirst().contains("database.type"));

        problems.clear();
        assertEquals(DatabaseSettings.Type.MARIADB, from("database:\n  type: mysql\n").database().type());
        assertTrue(problems.isEmpty(), "mysql is an alias for MariaDB");
    }

    @Test
    void tablePrefixIsCheckedAgainstTheSqlSafePattern() {
        for (String bad : List.of("bad prefix", "semi;colon", "drop--", "a".repeat(33), "naïve")) {
            problems.clear();
            PluginSettings s = from("database:\n  table-prefix: \"" + bad + "\"\n");
            assertEquals("core_", s.database().tablePrefix(), "prefix '" + bad + "'");
            assertEquals(1, problems.size(), "prefix '" + bad + "'");
        }
        problems.clear();
        assertEquals("", from("database:\n  table-prefix: \"\"\n").database().tablePrefix());
        assertEquals("a".repeat(32), from("database:\n  table-prefix: \"" + "a".repeat(32) + "\"\n").database().tablePrefix());
        assertTrue(problems.isEmpty(), "empty and 32-character prefixes are allowed");
    }

    @Test
    void portRangeEdgesAreUsedAndOutsideTheRangeFallsBack() {
        for (int bad : new int[] {0, -1, 65536, 99999}) {
            problems.clear();
            assertEquals(3306, from("database:\n  port: " + bad + "\n").database().port(), "port " + bad);
            assertEquals(1, problems.size(), "port " + bad);
        }
        problems.clear();
        assertEquals(1, from("database:\n  port: 1\n").database().port());
        assertEquals(65535, from("database:\n  port: 65535\n").database().port());
        assertTrue(problems.isEmpty());
    }

    @Test
    void nonNumericPortIsReportedOnceAndFallsBackToDefault() {
        for (String bad : new String[] {"abc", "'3306x'", "true", "''", "[1, 2]"}) {
            problems.clear();
            assertEquals(3306, from("database:\n  port: " + bad + "\n").database().port(), bad);
            assertEquals(1, problems.size(), bad + " -> " + problems);
            assertTrue(problems.getFirst().contains("is not a number; using 3306"), problems.getFirst());
        }
    }

    @Test
    void aQuotedWholeNumberIsUsedNotIgnored() {
        assertEquals(3308, from("database:\n  port: '3308'\n").database().port());
        assertEquals(3308, from("database:\n  port: ' 3308 '\n").database().port());
        assertTrue(problems.isEmpty(), "F-12: " + problems);
    }

    @Test
    void aQuotedOutOfRangeNumberIsReported() {
        assertEquals(3306, from("database:\n  port: '70000'\n").database().port());
        assertEquals(1, problems.size(), problems.toString());
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
                """);
        assertEquals(4, problems.size(), problems.toString());
        assertEquals(DatabaseSettings.Type.H2, s.database().type());
    }

    /** chat.enabled is read; a value that is not true or false is reported and the chat stays on. */
    @Test
    void chatSwitchIsReadAndABadValueFallsBackToOn() {
        assertFalse(from("chat:\n  enabled: false\n").chatEnabled());
        assertTrue(problems.isEmpty(), problems.toString());
        assertTrue(from("chat:\n  enabled: maybe\n").chatEnabled());
        assertEquals(1, problems.size(), problems.toString());
    }

    /** tab.enabled is read like chat.enabled: off is kept, a value that is not true or false reports and stays on. */
    @Test
    void tabSwitchIsReadAndABadValueFallsBackToOn() {
        assertFalse(from("tab:\n  enabled: false\n").tabEnabled());
        assertTrue(problems.isEmpty(), problems.toString());
        assertTrue(from("tab:\n  enabled: maybe\n").tabEnabled());
        assertEquals(1, problems.size(), problems.toString());
    }

    @Test
    void withDatabaseReplacesOnlyTheDatabase() {
        PluginSettings a = from("database:\n  file: a/file\n");
        PluginSettings b = from("server:\n  name: B\ndatabase:\n  type: mariadb\n  host: other\n");
        PluginSettings merged = b.withDatabase(a.database());
        assertEquals("B", merged.serverName());
        assertSame(a.database(), merged.database());
    }

    @Test
    void passwordNeverAppearsInTextOutput() {
        PluginSettings s = from("database:\n  type: mariadb\n  password: s3cret\n");
        assertFalse(s.toString().contains("s3cret"), s.toString());
        assertFalse(s.database().describe().contains("s3cret"), s.database().describe());
        assertTrue(s.database().toString().contains("password=***"), "a set password is shown as ***: " + s.database());
    }
}
