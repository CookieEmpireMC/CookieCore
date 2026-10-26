package dev.casperrs.cookieCore.scoreboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.casperrs.cookieCore.TestSupport;
import dev.casperrs.cookieCore.log.WarnOnce;
import java.lang.reflect.Proxy;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

class PlaceholderRegistryTest {

    private final TestSupport.LogCapture log = new TestSupport.LogCapture();
    private final PlaceholderRegistry registry = new PlaceholderRegistry(new WarnOnce(log.logger));

    /** A plugin stand-in: only {@code getName} is meaningful, identity is by object. */
    private static Plugin plugin(String name) {
        return (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(), new Class<?>[] {Plugin.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
    }

    private static Component text(String s) {
        return Component.text(s);
    }

    @Test
    void keysMustBeLowercaseWordsAndNeverABuiltIn() {
        Plugin owner = plugin("clicker");
        for (String bad : List.of("Cps", "cookies-per-second", "a b", "", "player", "ping")) {
            assertThrows(IllegalArgumentException.class, () -> registry.register(owner, bad, p -> text("x")), bad);
        }
        assertEquals(0, registry.version(), "rejected registrations change nothing");
        registry.register(owner, "cookies_per_second", p -> text("1"));
        assertEquals(List.of("cookies_per_second"), registry.keys());
    }

    @Test
    void aDuplicateKeyIsRejectedNamingTheFirstOwnerAndChangesNothing() {
        registry.register(plugin("clicker"), "cps", p -> text("1"));
        int version = registry.version();
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> registry.register(plugin("other"), "cps", p -> text("2")));
        assertTrue(e.getMessage().contains("cps") && e.getMessage().contains("clicker"), e.getMessage());
        assertEquals(version, registry.version());
    }

    /** Unregistering removes only the owner's keys; the version moves so templates compile again, and only then. */
    @Test
    void unregisterAllRemovesOnlyTheOwnersKeysAndBumpsTheVersionOnce() {
        Plugin clicker = plugin("clicker");
        registry.register(clicker, "cps", p -> text("1"));
        registry.register(clicker, "cookies", p -> text("2"));
        registry.register(plugin("other"), "rank", p -> text("3"));
        int before = registry.version();

        registry.unregisterAll(clicker);
        assertEquals(List.of("rank"), registry.keys());
        assertEquals(before + 1, registry.version());

        registry.unregisterAll(clicker);
        assertEquals(before + 1, registry.version(), "nothing left to remove, nothing changed");
    }

    /** A throwing or null resolver shows nothing, and each key's problem is logged once however often it repeats. */
    @Test
    void aBrokenResolverRendersEmptyAndIsLoggedOncePerKey() {
        Plugin owner = plugin("clicker");
        registry.register(owner, "boom", p -> {
            throw new IllegalStateException("bad state");
        });
        registry.register(owner, "nothing", p -> null);
        Player player = TestSupport.player(new TestSupport.Recorder());

        for (int i = 0; i < 3; i++) {
            assertEquals("", plain(registry.resolve("boom", player)));
            assertEquals("", plain(registry.resolve("nothing", player)));
        }
        assertEquals(2, log.messages().size(), log.messages().toString());
        assertTrue(log.messages().getFirst().contains("boom") && log.messages().getFirst().contains("clicker"), log.messages().toString());
    }

    /** Only the keys a layout uses become resolver calls; a registered key the layout ignores is never resolved. */
    @Test
    void theLayoutDecidesWhichResolversRun() {
        registry.register(plugin("clicker"), "cps", p -> text("1"));
        registry.register(plugin("clicker"), "unused", p -> text("2"));
        ScoreboardLayout layout = new ScoreboardLayout("<cps>", List.of("", "<gray>x"));

        CompiledLayout compiled = CompiledLayout.compile(layout, registry.version(), registry.keys());
        assertEquals(List.of("cps"), compiled.keys());
        assertFalse(compiled.keys().contains("unused"));
    }

    private static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }
}
