package dev.casperrs.cookieCore.tab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.casperrs.cookieCore.TestSupport;
import dev.casperrs.cookieCore.log.WarnOnce;
import dev.casperrs.cookieCore.rank.RankSource;
import dev.casperrs.cookieCore.scoreboard.PlaceholderRegistry;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

class TabServiceTest {

    private static final String TAB = """
            header:
              - "<server> <online>/<max>"
            footer:
              - "<ping> <tps>"
            name-format: "<rank_prefix><player>"
            """;

    private final TestSupport.LogCapture log = new TestSupport.LogCapture();
    private final PlaceholderRegistry registry = new PlaceholderRegistry(new WarnOnce(log.logger));
    private final List<String> packets = new ArrayList<>();
    private final Map<String, Integer> orders = new HashMap<>();
    private final Map<String, Integer> pings = new HashMap<>();
    private final Set<String> failingPings = new HashSet<>();
    private final Map<String, Component> prefixes = new HashMap<>();
    private final Map<String, Integer> rankOrders = new HashMap<>();
    private final List<Player> online = new ArrayList<>();
    private final List<Runnable> tasks = new ArrayList<>();
    private final RankSource ranks = new RankSource() {
        @Override
        public Optional<Component> prefix(Player player) {
            return Optional.ofNullable(prefixes.get(player.getName()));
        }

        @Override
        public int order(Player player) {
            return rankOrders.getOrDefault(player.getName(), 0);
        }
    };
    private double tps = 20.0;
    private boolean enabled = true;
    private int starts;
    private int cancels;
    private TabService service;

    TabServiceTest() {
        service = newService(TAB);
    }

    @Test
    void joinSendsHeaderFooterAndNameOnceAndAnUnchangedTickSendsNothing() {
        connect(player("Steve"));
        assertEquals(List.of("hf Steve Cookie Empire 1/20|25 20.0", "name Steve Steve"), take());
        tick();
        assertEquals(List.of(), take(), "nothing changed, nothing sent");
    }

    @Test
    void tpsJitterInsideTheShownDigitSendsNothingAndAChangeResendsHeaderAndFooterOnly() {
        connect(player("Steve"));
        take();
        tps = 19.96;
        tick();
        assertEquals(List.of(), take(), "19.96 shows as 20.0 like before");
        tps = 19.4;
        tick();
        assertEquals(List.of("hf Steve Cookie Empire 1/20|25 19.4"), take());
    }

    @Test
    void aPingChangeResendsHeaderAndFooterOnly() {
        connect(player("Steve"));
        take();
        pings.put("Steve", 31);
        tick();
        assertEquals(List.of("hf Steve Cookie Empire 1/20|31 20.0"), take());
    }

    @Test
    void theTaskStartsWithTheFirstViewerStopsWithTheLastAndAQuitForgetsWhatWasSent() {
        Player steve = player("Steve");
        Player alex = player("Alex");
        connect(steve);
        connect(alex);
        assertEquals(1, starts, "one task for every viewer");
        quit(steve);
        assertEquals(0, cancels, "Alex still watches");
        quit(alex);
        assertEquals(1, cancels, "no viewer left, the task stops");
        take();
        connect(steve);
        assertEquals(2, starts);
        assertEquals(List.of("hf Steve Cookie Empire 1/20|25 20.0", "name Steve Steve"), take(),
                "a rejoin sends everything again, the old cache is gone");
    }

    @Test
    void aReloadSendsEverythingOnceMoreEvenWhenTheTextIsTheSame() {
        connect(player("Steve"));
        take();
        service.reconfigure();
        assertEquals(List.of("hf Steve Cookie Empire 1/20|25 20.0", "name Steve Steve"), take());
        tick();
        assertEquals(List.of(), take());
    }

    @Test
    void switchingTheTabListOffResetsEachViewerOnceAndStopsTheTask() {
        connect(player("Steve"));
        take();
        enabled = false;
        service.reconfigure();
        assertEquals(List.of("name Steve <reset>", "order Steve 0", "hf Steve |"), take());
        assertEquals(1, cancels);
        service.reconfigure();
        assertEquals(List.of(), take(), "a second reload while off sends nothing");
    }

    @Test
    void aTabListSwitchedOffAtStartupSendsNothingAndStartsNoTask() {
        enabled = false;
        connect(player("Steve"));
        assertEquals(List.of(), take());
        assertEquals(0, starts);
    }

    @Test
    void aViewerWhoseRefreshThrowsIsLoggedAndDroppedWhileTheOthersKeepTheirTab() {
        failingPings.add("Bad");
        connect(player("Bad"));
        connect(player("Steve"));
        take();
        assertTrue(log.messages().stream().anyMatch(m -> m.contains("Could not update the tab list of Bad")), log.messages().toString());
        tps = 19.0;
        tick();
        assertEquals(List.of("hf Steve Cookie Empire 2/20|25 19.0"), take());
    }

    @Test
    void aPluginsPlaceholderFillsItsKeyAndTheBuiltInTpsWins() {
        registry.register(plugin("clicker"), "cps", p -> Component.text("42"));
        registry.register(plugin("impostor"), "tps", p -> Component.text("999"));
        service = newService("header:\n  - \"<cps> <tps>\"\nfooter: []\nname-format: \"<player>\"\n");
        connect(player("Steve"));
        assertEquals(List.of("hf Steve 42 20.0|", "name Steve Steve"), take());
    }

    @Test
    void theRankOrderIsClampedToZeroAndSentOnlyWhenSet() {
        prefixes.put("Steve", Component.text("[Admin] "));
        rankOrders.put("Steve", -4);
        rankOrders.put("Alex", 2);
        connect(player("Steve"));
        assertEquals(List.of("hf Steve Cookie Empire 1/20|25 20.0", "name Steve [Admin] Steve"), take());
        connect(player("Alex"));
        assertEquals(List.of("order Alex 2", "hf Alex Cookie Empire 2/20|25 20.0", "name Alex Alex"), take());
    }

    @Test
    void tpsIsOneDecimalCappedAtTwentyAndNeverNegativeOrNaN() {
        assertEquals("20.0", TabService.formatTps(20.7));
        assertEquals("12.3", TabService.formatTps(12.34));
        assertEquals("0.0", TabService.formatTps(-1.0));
        assertEquals("0.0", TabService.formatTps(Double.NaN));
    }

    private TabService newService(String yaml) {
        TabConfig config = new TabConfig(() -> Optional.of(TestSupport.yaml(yaml)), Optional::empty);
        config.load(new ArrayList<>());
        return new TabService((task, period) -> {
            starts++;
            tasks.add(task);
            return () -> cancels++;
        }, log.logger, registry, config, new FakeSink(), ranks, () -> enabled, () -> "Cookie Empire");
    }

    private void connect(Player player) {
        online.add(player);
        service.join(player);
    }

    private void quit(Player player) {
        online.remove(player);
        service.quit(player);
    }

    /** Runs the task the service started last; the test never runs a cancelled one. */
    private void tick() {
        tasks.get(tasks.size() - 1).run();
    }

    private List<String> take() {
        List<String> out = List.copyOf(packets);
        packets.clear();
        return out;
    }

    /** Records each Paper call as one line; the header and footer go out as one packet, like Paper does. */
    private final class FakeSink implements TabSink {
        @Override
        public void listName(Player player, Component name) {
            packets.add("name " + player.getName() + " " + (name == null ? "<reset>" : plain(name)));
        }

        @Override
        public void order(Player player, int order) {
            orders.put(player.getName(), order);
            packets.add("order " + player.getName() + " " + order);
        }

        @Override
        public int order(Player player) {
            return orders.getOrDefault(player.getName(), 0);
        }

        @Override
        public void headerAndFooter(Player player, Component header, Component footer) {
            packets.add("hf " + player.getName() + " " + plain(header) + "|" + plain(footer));
        }

        @Override
        public int ping(Player player) {
            if (failingPings.contains(player.getName())) {
                throw new IllegalStateException("no connection");
            }
            return pings.getOrDefault(player.getName(), 25);
        }

        @Override
        public double tps() {
            return tps;
        }

        @Override
        public int online() {
            return online.size();
        }

        @Override
        public int maxPlayers() {
            return 20;
        }

        @Override
        public Collection<? extends Player> onlinePlayers() {
            return online;
        }
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    /** A Player stand-in: name and identity only (the service keys by UUID and reads the name). */
    private static Player player(String name) {
        UUID id = UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "getUniqueId" -> id;
                    case "isOnline" -> true;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "Player(" + name + ")";
                    default -> null;
                });
    }

    /** A Plugin stand-in for the registry: only the name matters. */
    private static Plugin plugin(String name) {
        return (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(), new Class<?>[] {Plugin.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
    }
}
