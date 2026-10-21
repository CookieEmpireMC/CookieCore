package dev.casperrs.cookieCore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.casperrs.cookieCore.sound.SoundConfigParser;
import dev.casperrs.cookieCore.sound.SoundDef;
import dev.casperrs.cookieCore.sound.SoundEvents;
import dev.casperrs.cookieCore.config.YamlFiles;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** plugin.yml, pom.xml, Perm and sounds.yml must agree with each other and with the brief's contract. */
class PluginDescriptorTest {

    /** Raw map: permission nodes contain dots, which Bukkit's YamlConfiguration would split into nested sections. */
    private static final Map<String, Object> PLUGIN = loadPluginYml();

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadPluginYml() {
        try (java.io.InputStream in = PluginDescriptorTest.class.getResourceAsStream("/plugin.yml")) {
            return (Map<String, Object>) new org.yaml.snakeyaml.Yaml().load(in);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> permissions() {
        return (Map<String, Object>) PLUGIN.get("permissions");
    }

    @SuppressWarnings("unchecked")
    private static List<String> list(String key) {
        Object v = PLUGIN.get(key);
        return v == null ? List.of() : (List<String>) v;
    }

    @Test
    void basics() throws ClassNotFoundException {
        assertEquals("CookieCore", PLUGIN.get("name"));
        assertEquals("26.3", PLUGIN.get("api-version"));
        assertEquals(List.of("LuckPerms"), list("softdepend"));
        String main = (String) PLUGIN.get("main");
        assertEquals("dev.casperrs.cookieCore.CookieCore", main);
        Class<?> cls = Class.forName(main, false, getClass().getClassLoader());
        assertTrue(org.bukkit.plugin.java.JavaPlugin.class.isAssignableFrom(cls));
        assertTrue(list("depend").isEmpty(), "nothing is a hard dependency");
        assertEquals(null, PLUGIN.get("commands"), "commands are registered through Brigadier, not plugin.yml (ROADMAP D5)");
    }

    @Test
    void versionIsFilteredByMaven() {
        String version = String.valueOf(PLUGIN.get("version"));
        assertNotNull(version);
        assertTrue(!version.contains("${"), "plugin.yml resource filtering did not run: " + version);
    }

    @Test
    void everyPermConstantIsDeclaredInPluginYml() {
        Set<String> declared = new HashSet<>();
        for (String node : permissions().keySet()) {
            if (!node.endsWith(".*")) {
                declared.add(node);
            }
        }
        assertEquals(new HashSet<>(Perm.ALL), declared, "Perm.ALL and plugin.yml permissions differ");
    }

    @Test
    void permAllListsEveryPublicConstant() throws IllegalAccessException {
        Set<String> constants = new HashSet<>();
        for (Field f : Perm.class.getDeclaredFields()) {
            if (Modifier.isPublic(f.getModifiers()) && Modifier.isStatic(f.getModifiers()) && f.getType() == String.class) {
                constants.add((String) f.get(null));
            }
        }
        assertEquals(constants, new HashSet<>(Perm.ALL), "a Perm constant is missing from Perm.ALL");
        assertEquals(Perm.ALL.size(), new HashSet<>(Perm.ALL).size(), "duplicate nodes in Perm.ALL");
    }

    @Test
    void permNodesFollowTheNamingConvention() {
        for (String node : Perm.ALL) {
            assertTrue(node.matches("cookiecore\\.[a-z]+\\.[a-z-]+"), node);
        }
        assertEquals("cookiecore.admin.reload", Perm.ADMIN_RELOAD);
        assertEquals("cookiecore.admin.version", Perm.ADMIN_VERSION);
    }

    @Test
    @SuppressWarnings("unchecked")
    void wildcardAdminNodeCoversAllAdminNodesAndEveryNodeHasADescriptionAndDefault() {
        Map<String, Object> wildcard = (Map<String, Object>) permissions().get("cookiecore.admin.*");
        assertNotNull(wildcard, "cookiecore.admin.* is declared");
        Map<String, Object> children = (Map<String, Object>) wildcard.get("children");
        Set<String> adminNodes = new HashSet<>();
        for (String n : Perm.ALL) {
            if (n.startsWith("cookiecore.admin.")) {
                adminNodes.add(n);
            }
        }
        assertEquals(adminNodes, children.keySet(), "cookiecore.admin.* must list exactly the admin nodes");
        for (String node : Perm.ALL) {
            Map<String, Object> spec = (Map<String, Object>) permissions().get(node);
            assertNotNull(spec.get("description"), node + " has no description");
            assertEquals("op", spec.get("default"), node + " should default to op");
        }
    }

    @Test
    void jdbcLibrariesMatchThePomProperties() throws IOException {
        String pom = Files.readString(Path.of("pom.xml"));
        String h2 = property(pom, "h2.version");
        String maria = property(pom, "mariadb.version");
        List<String> libs = list("libraries");
        assertTrue(libs.contains("com.h2database:h2:" + h2), libs.toString());
        assertTrue(libs.contains("org.mariadb.jdbc:mariadb-java-client:" + maria), libs.toString());
        assertEquals(2, libs.size());
    }

    private static String property(String pom, String name) {
        Matcher m = Pattern.compile("<" + Pattern.quote(name) + ">([^<]+)</").matcher(pom);
        assertTrue(m.find(), name + " not in pom.xml");
        return m.group(1).trim();
    }

    @Test
    void pomPinsPaper263AndNeverShadesAnything() throws IOException {
        String pom = Files.readString(Path.of("pom.xml"));
        assertTrue(pom.contains("<artifactId>paper-api</artifactId>"));
        assertTrue(pom.contains("<version>26.3.build.205-stable</version>"), "Paper must stay pinned to the -stable build");
        assertTrue(pom.contains("<java.version>25</java.version>"));
        assertTrue(!pom.contains("maven-shade-plugin") && !pom.contains("maven-assembly-plugin"), "nothing may be shaded");
        assertTrue(!pom.contains("-R0.1-SNAPSHOT") && !pom.contains("[26"), "no 1.21.x leftovers or open version ranges");
        // every non-test dependency must be provided
        Matcher m = Pattern.compile("<dependency>(.*?)</dependency>", Pattern.DOTALL).matcher(pom);
        List<String> notProvided = new ArrayList<>();
        while (m.find()) {
            String dep = m.group(1);
            if (!dep.contains("<scope>provided</scope>") && !dep.contains("<scope>test</scope>")) {
                notProvided.add(dep.strip());
            }
        }
        assertTrue(notProvided.isEmpty(), "dependencies that would be shaded or leak: " + notProvided);
    }

    // --- sounds.yml and the brief's contract ---------------------------------------------------------------------

    @Test
    void shippedSoundsParseWithoutProblems() {
        List<String> problems = new ArrayList<>();
        Map<String, SoundDef> sounds = SoundConfigParser.parse(YamlFiles.toMap(TestSupport.resourceYaml("sounds.yml")), problems::add);
        assertTrue(problems.isEmpty(), problems.toString());
        assertTrue(sounds.keySet().containsAll(List.of(SoundEvents.UI_CONFIRM, SoundEvents.UI_ERROR, SoundEvents.UI_NOTIFY)));
    }

    @Test
    void contractSoundEventsHaveTheAgreedVanillaKeysAndTuning() {
        Map<String, SoundDef> sounds = SoundConfigParser.parse(YamlFiles.toMap(TestSupport.resourceYaml("sounds.yml")), p -> { });
        assertSound(sounds.get("ui_confirm"), "minecraft:block.note_block.pling", 0.5f, 1.4f);
        assertSound(sounds.get("ui_error"), "minecraft:block.note_block.bass", 0.5f, 0.8f);
        assertSound(sounds.get("ui_notify"), "minecraft:entity.experience_orb.pickup", 0.4f, 1.2f);
    }

    private static void assertSound(SoundDef d, String key, float volume, float pitch) {
        assertNotNull(d);
        assertEquals(key, d.fallback());
        assertEquals(key, d.key(), "Core ships no pack: key and fallback are both vanilla");
        assertTrue(d.key().startsWith("minecraft:"));
        assertEquals(volume, d.volume());
        assertEquals(pitch, d.pitchMin());
        assertEquals(pitch, d.pitchMax());
    }

    @Test
    void everySoundEventConstantExistsInSoundsYml() throws IllegalAccessException {
        YamlConfiguration yml = TestSupport.resourceYaml("sounds.yml");
        for (Field f : SoundEvents.class.getDeclaredFields()) {
            if (Modifier.isPublic(f.getModifiers()) && f.getType() == String.class) {
                String event = (String) f.get(null);
                assertTrue(yml.isConfigurationSection(event), "SoundEvents." + f.getName() + " = '" + event + "' is not in sounds.yml");
            }
        }
    }

    @Test
    void configYmlHasTheContractKeys() {
        YamlConfiguration cfg = TestSupport.resourceYaml("config.yml");
        for (String key : List.of("server.name", "database.type", "database.file", "database.host", "database.port",
                "database.name", "database.user", "database.password")) {
            assertTrue(cfg.isSet(key), "config.yml lacks contract key " + key);
        }
        assertEquals("h2", cfg.getString("database.type"), "H2 is the default");
    }
}
