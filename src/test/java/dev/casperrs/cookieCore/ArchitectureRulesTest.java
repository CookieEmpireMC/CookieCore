package dev.casperrs.cookieCore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Source-scan checks of the project rules in docs/architecture.md (same approach as CookieClicker; no ArchUnit
 * dependency). Comments are ignored; paths are normalised so the rules also hold on Windows.
 */
class ArchitectureRulesTest {

    /** Overridable so the rules themselves can be exercised against a deliberately broken copy of the sources. */
    private static final Path MAIN = Path.of(System.getProperty("cookiecore.mainSources", "src/main/java"));
    private static final String BASE = "dev.casperrs.cookieCore";

    /** One line of production code (comment lines removed). */
    private record Line(String file, int number, String text) {
        String where() {
            return file + ":" + number;
        }

        /** Package folder of the file relative to the base package: "" for the root, else "storage", "command", ... */
        String pkg() {
            int slash = file.lastIndexOf('/');
            return slash < 0 ? "" : file.substring(0, slash);
        }
    }

    private static List<String> files() throws IOException {
        try (Stream<Path> s = Files.walk(MAIN)) {
            return s.filter(p -> p.toString().endsWith(".java"))
                    .map(p -> MAIN.resolve(BASE.replace('.', '/')).relativize(p).toString().replace('\\', '/'))
                    .sorted().toList();
        }
    }

    private static List<Line> code() throws IOException {
        List<Line> out = new ArrayList<>();
        for (String file : files()) {
            List<String> lines = Files.readAllLines(MAIN.resolve(BASE.replace('.', '/')).resolve(file));
            boolean inBlock = false;
            for (int i = 0; i < lines.size(); i++) {
                String t = lines.get(i).trim();
                if (inBlock) {
                    if (t.contains("*/")) {
                        inBlock = false;
                    }
                    continue;
                }
                if (t.startsWith("/*")) {
                    inBlock = !t.contains("*/");
                    continue;
                }
                if (t.startsWith("//") || t.startsWith("*")) {
                    continue;
                }
                out.add(new Line(file, i + 1, t));
            }
        }
        return out;
    }

    /** Lines containing {@code needle}, except in files under {@code allowedFolder} (null = nowhere allowed). */
    private static List<String> hits(String needle, String allowedFolder) throws IOException {
        List<String> out = new ArrayList<>();
        for (Line l : code()) {
            if (allowedFolder != null && l.pkg().equals(allowedFolder)) {
                continue;
            }
            if (l.text().contains(needle)) {
                out.add(l.where());
            }
        }
        return out;
    }

    private static void assertNone(List<String> hits, String rule) {
        assertTrue(hits.isEmpty(), rule + " violated at " + hits);
    }

    @Test
    void theScanActuallySeesTheSources() throws IOException {
        assertTrue(files().size() > 20, files().toString());
        assertTrue(files().contains("CookieCore.java"));
        assertFalse(hits("package ", null).isEmpty());
    }

    // --- sounds and particles ------------------------------------------------------------------------------------

    @Test
    void playSoundOnlyInTheSoundPackage() throws IOException {
        assertNone(hits("playSound(", "sound"), "no playSound outside sound/");
        assertFalse(hits("playSound(", null).isEmpty(), "the sound service should be the one place that plays");
    }

    @Test
    void noOtherWayToMakeNoiseOutsideTheSoundPackage() throws IOException {
        for (String needle : new String[] {"stopSound(", "Sound.sound(", "Sound.Emitter", "Sound.Source", "NamedSoundEntity"}) {
            assertNone(hits(needle, "sound"), needle + " outside sound/");
        }
    }

    @Test
    void spawnParticleNowhere() throws IOException {
        assertNone(hits("spawnParticle(", null), "no spawnParticle (Core has no effects engine)");
        assertNone(hits("Particle.", null), "no Particle usage");
    }

    // --- storage purity and threading ----------------------------------------------------------------------------

    @Test
    void storageHasNoBukkitPaperOrAdventureImports() throws IOException {
        List<String> found = new ArrayList<>();
        for (Line l : code()) {
            if (l.pkg().equals("storage") && l.text().startsWith("import ")
                    && (l.text().startsWith("import org.bukkit") || l.text().startsWith("import io.papermc")
                    || l.text().startsWith("import net.kyori") || l.text().startsWith("import com.mojang"))) {
                found.add(l.where());
            }
            if (l.pkg().equals("storage") && (l.text().contains("Bukkit.") || l.text().contains("org.bukkit."))) {
                found.add(l.where());
            }
        }
        assertNone(found, "no Bukkit calls from storage/");
    }

    @Test
    void storageDoesNotDependOnOtherCorePackages() throws IOException {
        List<String> found = new ArrayList<>();
        for (Line l : code()) {
            if (l.pkg().equals("storage") && l.text().startsWith("import " + BASE + ".") && !l.text().startsWith("import " + BASE + ".storage.")) {
                found.add(l.where());
            }
        }
        assertNone(found, "storage/ must stay a leaf package");
    }

    @Test
    void jdbcOnlyInStorage() throws IOException {
        List<String> found = new ArrayList<>();
        for (Line l : code()) {
            if (!l.pkg().equals("storage") && (l.text().contains("java.sql.") || l.text().contains("DriverManager"))) {
                found.add(l.where());
            }
        }
        assertNone(found, "JDBC only in storage/");
    }

    @Test
    void noBlockingWaitsOnFuturesAndNoSleeps() throws IOException {
        assertNone(hits(".join()", null), "CompletableFuture.join() blocks the caller");
        assertNone(hits("Thread.sleep(", null), "no sleeping");
        List<String> futureGets = new ArrayList<>();
        for (Line l : code()) {
            if (l.text().matches(".*\\b(future|result|done|f)\\.get\\(.*")) {
                futureGets.add(l.where());
            }
        }
        assertNone(futureGets, "Future.get() blocks the caller");
    }

    @Test
    void onlyTheStorageThreadClassCreatesThreads() throws IOException {
        for (String needle : new String[] {"new Thread(", "Executors.new", "ForkJoinPool", "CompletableFuture.supplyAsync", "CompletableFuture.runAsync", "runTaskAsynchronously"}) {
            List<String> found = new ArrayList<>();
            for (Line l : code()) {
                if (l.text().contains(needle) && !l.file().equals("storage/StorageThread.java")) {
                    found.add(l.where());
                }
            }
            assertNone(found, needle + " outside StorageThread (one IO thread for all database work)");
        }
    }

    @Test
    void schedulerAndMainThreadHandoffLiveInTheMainClassOnly() throws IOException {
        List<String> found = new ArrayList<>();
        for (Line l : code()) {
            if ((l.text().contains("getScheduler()") || l.text().contains("runTask(")) && !l.file().equals("CookieCore.java")) {
                found.add(l.where());
            }
        }
        assertNone(found, "scheduler use outside CookieCore");
    }

    // --- player-facing text --------------------------------------------------------------------------------------

    @Test
    void noPlayerFacingTextOutsideTheTextPackage() throws IOException {
        String[] senders = {".sendMessage(", ".sendActionBar(", ".sendRichMessage(", ".sendPlainMessage(", ".showTitle(",
                ".sendTitle(", ".sendPlayerListHeader", ".kick(", ".kickPlayer(", ".disallow(", ".kickMessage(",
                "Component.text(", "Component.translatable(", "Component.textOfChildren(", "MiniMessage", "LegacyComponentSerializer",
                "PlainTextComponentSerializer", "new LiteralMessage(", "SimpleCommandExceptionType(", "setPlayerListName(", ".playerListName("};
        List<String> found = new ArrayList<>();
        for (Line l : code()) {
            if (l.pkg().equals("text")) {
                continue;
            }
            for (String s : senders) {
                if (l.text().contains(s)) {
                    found.add(l.where() + " (" + s + ")");
                }
            }
        }
        assertNone(found, "text must come from text/Messages (messages.yml); no literals sent or built elsewhere");
    }

    @Test
    void messageKeysAreNeverBuiltFromStringLiteralsAsMessages() throws IOException {
        // every messages.send/component call takes a key (dotted lower-case) or a variable, never a sentence
        Pattern call = Pattern.compile("messages\\.(?:send|component|raw)\\([^,]*?,?\\s*\"([^\"]*)\"");
        List<String> found = new ArrayList<>();
        for (Line l : code()) {
            Matcher m = call.matcher(l.text());
            while (m.find()) {
                if (!m.group(1).matches("[a-z0-9]+(\\.[a-z0-9-]+)+")) {
                    found.add(l.where() + " '" + m.group(1) + "'");
                }
            }
        }
        assertNone(found, "message literal instead of a key");
    }

    @Test
    void playerInputIsNeverParsedAsMiniMessage() throws IOException {
        List<String> parsed = hits("Placeholder.parsed(", null);
        assertEquals(List.of("text/Messages.java:100"), parsed.stream().map(h -> h.replaceAll(":\\d+$", ":100")).toList(),
                "Placeholder.parsed is only for the trusted prefix in Messages: " + parsed);
        assertNone(hits(".deserialize(", "text"), "MiniMessage.deserialize outside text/");
    }

    // --- forbidden APIs ------------------------------------------------------------------------------------------

    @Test
    void noWorldGuardNmsOrReflectionIntoTheServer() throws IOException {
        for (String bad : new String[] {"com.sk89q", "net.minecraft.", "org.bukkit.craftbukkit", "CraftPlayer", "CraftServer",
                "getDeclaredField", "getDeclaredMethod", "setAccessible(", "sun.misc.Unsafe"}) {
            assertNone(hits(bad, null), "forbidden: " + bad);
        }
        assertNone(hits("Class.forName(", "storage"), "reflection only to load the JDBC driver in storage/");
    }

    @Test
    void noLegacyTextAndNoDeprecatedPaperApi() throws IOException {
        for (String bad : new String[] {"ChatColor", "§", "PlayerLoginEvent", "setPlayerListHeaderFooter", "AsyncPlayerChatEvent",
                "getPlayerListName(", "kickPlayer(", "BaseComponent", "LegacyComponentSerializer", "System.out", "printStackTrace("}) {
            assertNone(hits(bad, null), "forbidden: " + bad);
        }
    }

    @Test
    void noStringBasedScoreboardApi() throws IOException {
        List<String> found = new ArrayList<>();
        for (Line l : code()) {
            if (l.text().contains("registerNewObjective(") && !l.text().contains("Criteria.")) {
                found.add(l.where());
            }
        }
        assertNone(found, "registerNewObjective with a String criteria is deprecated; use Criteria + Component");
    }

    @Test
    void luckPermsOnlyInItsOwnPackage() throws IOException {
        List<String> found = new ArrayList<>();
        for (Line l : code()) {
            if (l.text().contains("net.luckperms") && !l.pkg().equals("luckperms")) {
                found.add(l.where());
            }
        }
        assertNone(found, "net.luckperms outside luckperms/ (it is a soft dependency: a stray import breaks boot without it)");
    }

    @Test
    void worldNamesAreNotHardcoded() throws IOException {
        for (String bad : new String[] {"\"world_nether\"", "\"world_the_end\"", "getWorld(\""}) {
            assertNone(hits(bad, null), "world names/paths: " + bad);
        }
    }

    // --- structure -----------------------------------------------------------------------------------------------

    @Test
    void apiPackageExposesOnlyJavaTypes() throws IOException {
        List<String> found = new ArrayList<>();
        for (Line l : code()) {
            if (l.pkg().equals("api") && l.text().startsWith("import ")
                    && !l.text().startsWith("import java.") && !l.text().startsWith("import " + BASE + ".api.")) {
                found.add(l.where());
            }
        }
        assertNone(found, "api/ may only use java.* and itself (no Bukkit, Brigadier, LuckPerms or storage types)");
    }

    @Test
    void noMutableStaticState() throws IOException {
        Pattern staticField = Pattern.compile("^(?:(?:public|private|protected)\\s+)?static\\s+(?!final\\b)(?!class\\b|interface\\b|record\\b|enum\\b)[^(]*[;=].*$");
        List<String> found = new ArrayList<>();
        for (Line l : code()) {
            if (staticField.matcher(l.text()).matches()) {
                found.add(l.where() + " " + l.text());
            }
        }
        assertNone(found, "mutable static fields (players must not leak into statics)");
    }

    @Test
    void noStaticCollectionsOfPlayersOrWorlds() throws IOException {
        List<String> found = new ArrayList<>();
        for (Line l : code()) {
            if (l.text().contains("static") && l.text().matches(".*(Map|Set|List|Collection)<.*(Player|World|Entity|Location|Plugin).*>.*=.*")) {
                found.add(l.where());
            }
        }
        assertNone(found, "static collections holding Bukkit objects leak on reload");
    }

    @Test
    void mainClassOnlyWires() throws IOException {
        long lines = Files.readAllLines(MAIN.resolve(BASE.replace('.', '/')).resolve("CookieCore.java")).size();
        assertTrue(lines <= 200, "CookieCore.java has " + lines + " lines; move logic into the sub-packages");
    }

    @Test
    void listenersAreFinalClassesInTheirFeaturePackage() throws IOException {
        List<String> found = new ArrayList<>();
        for (String file : files()) {
            String text = Files.readString(MAIN.resolve(BASE.replace('.', '/')).resolve(file));
            if (text.contains("implements Listener") && !text.contains("public final class")) {
                found.add(file);
            }
        }
        assertNone(found, "listeners should be final");
    }

    @Test
    void noCyclesBetweenPackages() throws IOException {
        // edges: package -> packages it imports (Perm, a constants leaf in the root package, is exempt)
        Map<String, Set<String>> deps = new LinkedHashMap<>();
        Pattern imp = Pattern.compile("^import " + Pattern.quote(BASE) + "\\.([a-zA-Z]+)(?:\\.[a-zA-Z]+)*;$");
        for (Line l : code()) {
            String from = l.pkg().isEmpty() ? "(root)" : l.pkg();
            deps.computeIfAbsent(from, k -> new LinkedHashSet<>());
            Matcher m = imp.matcher(l.text());
            if (m.matches()) {
                String first = m.group(1);
                String to;
                if (Character.isUpperCase(first.charAt(0))) {
                    if (first.equals("Perm")) {
                        continue;
                    }
                    to = "(root)";
                } else {
                    to = first;
                }
                if (!to.equals(from)) {
                    deps.get(from).add(to);
                }
            }
        }
        List<String> cycle = findCycle(deps);
        assertTrue(cycle.isEmpty(), "package cycle: " + cycle + " in " + deps);
        assertTrue(deps.get("(root)").size() > 3, "the main class wires the others: " + deps);
        for (Map.Entry<String, Set<String>> e : deps.entrySet()) {
            if (!e.getKey().equals("(root)")) {
                assertFalse(e.getValue().contains("(root)"), e.getKey() + "/ imports the main package: " + e.getValue());
            }
        }
    }

    private static List<String> findCycle(Map<String, Set<String>> deps) {
        for (String start : deps.keySet()) {
            List<String> path = new ArrayList<>();
            if (dfs(start, start, deps, path, new LinkedHashSet<>())) {
                return path;
            }
        }
        return List.of();
    }

    private static boolean dfs(String start, String node, Map<String, Set<String>> deps, List<String> path, Set<String> seen) {
        for (String next : deps.getOrDefault(node, Set.of())) {
            path.add(next);
            if (next.equals(start)) {
                return true;
            }
            if (seen.add(next) && dfs(start, next, deps, path, seen)) {
                return true;
            }
            path.removeLast();
        }
        return false;
    }
}
