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
 * Source-scan checks of the rules in docs/architecture.md (no ArchUnit dependency). Comments are ignored; paths are
 * normalised so the rules also hold on Windows. The config package importing sound, storage and text is a known
 * one-way dependency, not a cycle, so it is not asserted here (see the core-tidy review follow-ups).
 */
class ArchitectureRulesTest {

    /** Overridable so the rules can be exercised against a deliberately broken copy of the sources. */
    private static final Path MAIN = Path.of(System.getProperty("cookiecore.mainSources", "src/main/java"));
    private static final String BASE = "dev.casperrs.cookieCore";

    /** One line of production code (comment lines removed). */
    private record Line(String file, int number, String text) {
        String where() {
            return file + ":" + number;
        }

        /** Package folder relative to the base package: "" for the root, else "storage", "command", ... */
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
    }

    @Test
    void soundIsPlayedOnlyThroughTheSoundPackage() throws IOException {
        for (String needle : new String[] {"stopSound(", "Sound.sound(", "Sound.Emitter", "Sound.Source", "NamedSoundEntity"}) {
            assertNone(hits(needle, "sound"), needle + " outside sound/");
        }
        assertNone(hits("playSound(", "sound"), "playSound outside sound/");
        assertFalse(hits("playSound(", null).isEmpty(), "the sound service is the one place that plays");
    }

    @Test
    void storageIsALeafWithNoBukkitAndJdbcStaysInside() throws IOException {
        List<String> found = new ArrayList<>();
        for (Line l : code()) {
            boolean storage = l.pkg().equals("storage");
            if (storage && (l.text().startsWith("import org.bukkit") || l.text().startsWith("import io.papermc")
                    || l.text().startsWith("import net.kyori") || l.text().startsWith("import com.mojang")
                    || l.text().contains("Bukkit.") || l.text().contains("org.bukkit."))) {
                found.add(l.where() + " (no Bukkit, Paper or Adventure in storage/)");
            }
            if (storage && l.text().startsWith("import " + BASE + ".") && !l.text().startsWith("import " + BASE + ".storage.")) {
                found.add(l.where() + " (storage/ must stay a leaf package)");
            }
            if (!storage && (l.text().contains("java.sql.") || l.text().contains("DriverManager"))) {
                found.add(l.where() + " (JDBC only in storage/)");
            }
            if (!storage && l.text().contains("Class.forName(")) {
                found.add(l.where() + " (reflection only to load the JDBC driver in storage/)");
            }
        }
        assertNone(found, "storage rules");
    }

    @Test
    void blockingWaitsAndThreadsStayInTheStorageThreadAndTheMainClass() throws IOException {
        assertNone(hits(".join()", null), "CompletableFuture.join() blocks the caller");
        assertNone(hits("Thread.sleep(", null), "no sleeping on the server");
        List<String> found = new ArrayList<>();
        for (Line l : code()) {
            if (l.text().matches(".*\\b(future|result|done|f)\\.get\\(.*")) {
                found.add(l.where() + " (Future.get blocks the caller)");
            }
            if (!l.file().equals("storage/StorageThread.java") && (l.text().contains("new Thread(")
                    || l.text().contains("Executors.new") || l.text().contains("ForkJoinPool")
                    || l.text().contains("CompletableFuture.supplyAsync") || l.text().contains("CompletableFuture.runAsync")
                    || l.text().contains("runTaskAsynchronously"))) {
                found.add(l.where() + " (one IO thread for all database work, in StorageThread)");
            }
            if ((l.text().contains("getScheduler()") || l.text().contains("runTask(")) && !l.file().equals("CookieCore.java")) {
                found.add(l.where() + " (scheduler use only in the main class)");
            }
        }
        assertNone(found, "threading rules");
    }

    @Test
    void playerFacingTextComesFromTextMessagesAndKeysAreKeys() throws IOException {
        String[] senders = {".sendMessage(", ".sendActionBar(", ".sendRichMessage(", ".sendPlainMessage(", ".showTitle(",
                ".sendTitle(", ".sendPlayerListHeader", ".kick(", ".kickPlayer(", ".disallow(", ".kickMessage(",
                "Component.text(", "Component.translatable(", "Component.textOfChildren(", "MiniMessage",
                "LegacyComponentSerializer", "PlainTextComponentSerializer", "new LiteralMessage(",
                "SimpleCommandExceptionType(", "setPlayerListName(", ".playerListName("};
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
        assertNone(found, "text must come from text/Messages (messages.yml)");

        Pattern call = Pattern.compile("messages\\.(?:send|component|raw)\\([^,]*?,?\\s*\"([^\"]*)\"");
        List<String> literals = new ArrayList<>();
        for (Line l : code()) {
            Matcher m = call.matcher(l.text());
            while (m.find()) {
                if (!m.group(1).matches("[a-z0-9]+(\\.[a-z0-9-]+)+")) {
                    literals.add(l.where() + " '" + m.group(1) + "'");
                }
            }
        }
        assertNone(literals, "message literal instead of a key");
    }

    @Test
    void playerInputIsNeverParsedAsMiniMessage() throws IOException {
        List<String> parsed = hits("Placeholder.parsed(", null);
        assertEquals(List.of("text/Messages.java:100"), parsed.stream().map(h -> h.replaceAll(":\\d+$", ":100")).toList(),
                "Placeholder.parsed is only for the trusted prefix in Messages: " + parsed);
        assertNone(hits(".deserialize(", "text"), "MiniMessage.deserialize outside text/");
    }

    @Test
    void forbiddenApisAreAbsent() throws IOException {
        String[] forbidden = {"com.sk89q", "net.minecraft.", "org.bukkit.craftbukkit", "CraftPlayer", "CraftServer",
                "getDeclaredField", "getDeclaredMethod", "setAccessible(", "sun.misc.Unsafe",
                "ChatColor", "§", "PlayerLoginEvent", "setPlayerListHeaderFooter", "AsyncPlayerChatEvent",
                "getPlayerListName(", "kickPlayer(", "BaseComponent", "System.out", "printStackTrace(",
                "spawnParticle(", "Particle.", "\"world_nether\"", "\"world_the_end\"", "getWorld(\""};
        for (String bad : forbidden) {
            assertNone(hits(bad, null), "forbidden: " + bad);
        }
        List<String> scoreboard = new ArrayList<>();
        for (Line l : code()) {
            if (l.text().contains("registerNewObjective(") && !l.text().contains("Criteria.")) {
                scoreboard.add(l.where());
            }
            if (l.text().contains("net.luckperms")) {
                scoreboard.add(l.where() + " (LuckPerms is an optional dependency: no imports yet)");
            }
        }
        assertNone(scoreboard, "string scoreboard criteria or stray LuckPerms import");
    }

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
    void noPlayerOrWorldObjectsHeldInStaticState() throws IOException {
        Pattern mutableStatic = Pattern.compile("^(?:(?:public|private|protected)\\s+)?static\\s+(?!final\\b)(?!class\\b|interface\\b|record\\b|enum\\b)[^(]*[;=].*$");
        List<String> found = new ArrayList<>();
        for (Line l : code()) {
            if (mutableStatic.matcher(l.text()).matches()) {
                found.add(l.where() + " " + l.text());
            }
            if (l.text().contains("static") && l.text().matches(".*(Map|Set|List|Collection)<.*(Player|World|Entity|Location|Plugin).*>.*=.*")) {
                found.add(l.where() + " (static collections of Bukkit objects leak on reload)");
            }
        }
        assertNone(found, "static state");
    }

    @Test
    void mainClassOnlyWiresAndPackagesDoNotCycle() throws IOException {
        long lines = Files.readAllLines(MAIN.resolve(BASE.replace('.', '/')).resolve("CookieCore.java")).size();
        assertTrue(lines <= 200, "CookieCore.java has " + lines + " lines; move logic into the sub-packages");

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
