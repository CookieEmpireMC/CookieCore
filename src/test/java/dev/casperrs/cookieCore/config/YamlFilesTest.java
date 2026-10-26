package dev.casperrs.cookieCore.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class YamlFilesTest {

    @TempDir
    Path dir;

    @Test
    void missingFileIsEmptyNotAnError() throws Exception {
        assertTrue(YamlFiles.load(dir.resolve("nope.yml").toFile(), "nope.yml").isEmpty());
    }

    @Test
    void malformedYamlNamesTheFileAndKeepsTheCause() throws Exception {
        Path f = dir.resolve("config.yml");
        Files.writeString(f, "server:\n  name: [unclosed\n\tbad: tab\n");
        YamlFiles.BadYamlException e = assertThrows(YamlFiles.BadYamlException.class,
                () -> YamlFiles.load(f.toFile(), "config.yml"));
        assertTrue(e.getMessage().startsWith("config.yml is not valid YAML"), e.getMessage());
        assertNotNull(e.getCause());
    }

    /** Sections become nested maps in file order; the sound parser reads lists and maps through this. */
    @Test
    void toMapFlattensSectionsToNestedMapsInOrder() throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString("z:\n  key: a\n  n: 2\na:\n  deep:\n    x: true\nlist:\n  - 1\n  - 2\n");
        Map<String, Object> map = YamlFiles.toMap(y);
        assertEquals(List.of("z", "a", "list"), List.copyOf(map.keySet()));
        Map<?, ?> z = assertInstanceOf(Map.class, map.get("z"));
        assertEquals("a", z.get("key"));
        Map<?, ?> deep = assertInstanceOf(Map.class, ((Map<?, ?>) map.get("a")).get("deep"));
        assertEquals(true, deep.get("x"));
        assertEquals(List.of(1, 2), map.get("list"));
    }
}
