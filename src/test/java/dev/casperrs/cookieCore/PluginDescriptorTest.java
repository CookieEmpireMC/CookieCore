package dev.casperrs.cookieCore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.casperrs.cookieCore.config.YamlFiles;
import dev.casperrs.cookieCore.sound.SoundConfigParser;
import dev.casperrs.cookieCore.sound.SoundEvents;
import io.papermc.paper.plugin.loader.PluginLoader;
import java.io.IOException;
import java.io.InputStream;
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
import org.junit.jupiter.api.Test;

/** paper-plugin.yml, pom.xml, the JDBC library list, Perm and sounds.yml must agree with each other. */
class PluginDescriptorTest {

    /** Raw map: permission nodes contain dots, which Bukkit's YamlConfiguration would split into nested sections. */
    private static final Map<String, Object> PLUGIN = loadPaperPluginYml();

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadPaperPluginYml() {
        try (InputStream in = PluginDescriptorTest.class.getResourceAsStream("/paper-plugin.yml")) {
            return (Map<String, Object>) new org.yaml.snakeyaml.Yaml().load(in);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> permissions() {
        return (Map<String, Object>) PLUGIN.get("permissions");
    }

    @Test
    @SuppressWarnings("unchecked")
    void paperPluginYmlNamesTheLoaderMainAndLuckPermsAsAServerDependency() throws ClassNotFoundException {
        assertEquals("CookieCore", PLUGIN.get("name"));
        assertEquals("26.3", PLUGIN.get("api-version"));
        assertTrue(!String.valueOf(PLUGIN.get("version")).contains("${"), "version is filtered by Maven");

        String loader = (String) PLUGIN.get("loader");
        assertEquals("dev.casperrs.cookieCore.CookieCoreLoader", loader);
        assertTrue(PluginLoader.class.isAssignableFrom(Class.forName(loader, false, getClass().getClassLoader())));

        String main = (String) PLUGIN.get("main");
        assertEquals("dev.casperrs.cookieCore.CookieCore", main);
        assertTrue(org.bukkit.plugin.java.JavaPlugin.class.isAssignableFrom(
                Class.forName(main, false, getClass().getClassLoader())));

        assertTrue(!PLUGIN.containsKey("softdepend") && !PLUGIN.containsKey("depend") && !PLUGIN.containsKey("libraries"),
                "no plugin.yml-style dependency lists: they are in paper-plugin.yml's dependencies section");
        Map<String, Object> deps = (Map<String, Object>) ((Map<String, Object>) PLUGIN.get("dependencies")).get("server");
        Map<String, Object> luckPerms = (Map<String, Object>) deps.get("LuckPerms");
        assertEquals("BEFORE", luckPerms.get("load"));
        assertEquals(false, luckPerms.get("required"));
        assertEquals(true, luckPerms.get("join-classpath"));
        assertEquals(1, deps.size(), "only LuckPerms is a server dependency: " + deps.keySet());
    }

    @Test
    void everyPermConstantIsDeclaredInPaperPluginYml() {
        Set<String> declared = new HashSet<>();
        for (String node : permissions().keySet()) {
            if (!node.endsWith(".*")) {
                declared.add(node);
            }
        }
        assertEquals(new HashSet<>(Perm.ALL), declared, "Perm.ALL and paper-plugin.yml permissions differ");
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
    void jdbcLibrariesAreTiedToThePomVersionsExactly() throws IOException {
        String pom = Files.readString(Path.of("pom.xml"));
        String h2 = property(pom, "h2.version");
        String maria = property(pom, "mariadb.version");
        assertEquals(List.of("com.h2database:h2:" + h2, "org.mariadb.jdbc:mariadb-java-client:" + maria),
                CookieCoreLoader.libraries(), "cookiecore-libraries.properties must list exactly the two drivers at the pom versions");
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

    // --- sounds.yml -----------------------------------------------------------------------------------------------

    @Test
    void shippedSoundsParseWithoutProblemsAndEveryEventConstantExists() throws IllegalAccessException {
        List<String> problems = new ArrayList<>();
        var yml = TestSupport.resourceYaml("sounds.yml");
        SoundConfigParser.parse(YamlFiles.toMap(yml), problems::add);
        assertTrue(problems.isEmpty(), problems.toString());
        for (Field f : SoundEvents.class.getDeclaredFields()) {
            if (Modifier.isPublic(f.getModifiers()) && f.getType() == String.class) {
                String event = (String) f.get(null);
                assertTrue(yml.isConfigurationSection(event), "SoundEvents." + f.getName() + " = '" + event + "' is not in sounds.yml");
            }
        }
    }
}
