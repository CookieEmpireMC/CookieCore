package dev.casperrs.cookieCore.moderation;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.casperrs.cookieCore.TestSupport;
import dev.casperrs.cookieCore.api.PlayerRef;
import dev.casperrs.cookieCore.command.CommandKit;
import dev.casperrs.cookieCore.command.KnownPlayerArgument;
import dev.casperrs.cookieCore.config.YamlFiles;
import dev.casperrs.cookieCore.player.PlayerDirectory;
import dev.casperrs.cookieCore.sound.SoundConfigParser;
import dev.casperrs.cookieCore.sound.SoundService;
import dev.casperrs.cookieCore.storage.JdbcPlayerStore;
import dev.casperrs.cookieCore.storage.PunishmentStore;
import dev.casperrs.cookieCore.storage.StorageException;
import dev.casperrs.cookieCore.text.Messages;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.argument.TestVanillaArgumentProvider;
import com.destroystokyo.paper.profile.PlayerProfile;
import io.papermc.paper.connection.PlayerLoginConnection;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Executor;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

/**
 * Real moderation services on an in-memory H2 database, with synchronous executors unless a test defers the storage
 * thread. Players are {@link TestPlayer} proxies; the command tests reach them through the vanilla selector stand-in.
 */
final class ModerationFixture {

    final TestSupport.LogCapture log = new TestSupport.LogCapture();
    final Messages messages = new Messages(log.logger);
    final SoundService sounds;
    final PlayerDirectory directory;
    final CommandKit kit;
    private final String url = "jdbc:h2:mem:mod" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1";
    final JdbcPlayerStore store;
    final BanList bans = new BanList();
    final CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
    final List<TestPlayer> players = new ArrayList<>();
    final Queue<Runnable> deferred = new ArrayDeque<>();
    boolean deferStorage;
    boolean storageUp = true;
    long now = 1_000_000_000_000L;
    final ModerationService service;

    ModerationFixture() throws StorageException {
        messages.load(TestSupport.resourceYaml("messages.yml"));
        sounds = new SoundService(log.logger, new Random(1), p -> false);
        sounds.load(SoundConfigParser.parse(YamlFiles.toMap(TestSupport.resourceYaml("sounds.yml")), p -> { }));
        directory = new PlayerDirectory(TestSupport.noStore(), Runnable::run, Runnable::run, log.logger, () -> 1L);
        directory.start();
        kit = new CommandKit(messages, sounds, directory);
        store = new JdbcPlayerStore(() -> DriverManager.getConnection(url), "core_", log.logger);
        store.init();
        bans.markLoaded();
        TestVanillaArgumentProvider.ONLINE.clear();
        service = service(store);
    }

    /** A service over {@code punishments} with this fixture's bans, messages and clock. */
    ModerationService service(PunishmentStore punishments) {
        Executor io = task -> {
            if (deferStorage) {
                deferred.add(task);
            } else {
                task.run();
            }
        };
        return new ModerationService(punishments, io, Runnable::run, bans, messages, sounds, log.logger, () -> now,
                () -> players.stream().filter(p -> p.online).map(p -> p.player).toList(), () -> storageUp);
    }

    /** Runs the storage tasks that were deferred, in order. */
    void runDeferred() {
        while (!deferred.isEmpty()) {
            deferred.poll().run();
        }
    }

    /** A player Core has seen, online, registered for the vanilla selector stand-in. */
    TestPlayer player(String name, String... permissions) {
        TestPlayer p = new TestPlayer(name, permissions);
        players.add(p);
        TestVanillaArgumentProvider.ONLINE.put(name, p.player);
        directory.seen(p.id, name);
        return p;
    }

    PlayerRef ref(TestPlayer p) {
        return new PlayerRef(p.id, p.name);
    }

    int run(CommandSender sender, String line) throws CommandSyntaxException {
        return dispatcher.execute(line, TestSupport.source(sender));
    }

    Connection raw() throws SQLException {
        return DriverManager.getConnection(url);
    }

    int count(String table) throws SQLException {
        try (Connection c = raw(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    /** A store whose inserts always fail, for the "nothing changed" path. Everything else goes to {@code real}. */
    static PunishmentStore failingInserts(PunishmentStore real) {
        return new PunishmentStore() {
            @Override
            public long insert(dev.casperrs.cookieCore.storage.NewPunishment punishment) throws StorageException {
                throw new StorageException("disk full");
            }

            @Override
            public List<dev.casperrs.cookieCore.storage.BanRecord> loadActiveBans(long now) throws StorageException {
                return real.loadActiveBans(now);
            }

            @Override
            public int deactivateBans(UUID target, UUID byUuid, String byName, String reason, long now) throws StorageException {
                return real.deactivateBans(target, byUuid, byName, reason, now);
            }
        };
    }

    /** Runs the pre-login handler for a player with {@code who}; stand-ins replace the profile and the connection (the shorter event constructors need the running server). */
    static AsyncPlayerPreLoginEvent login(ModerationListener listener, UUID who) {
        PlayerProfile profile = (PlayerProfile) Proxy.newProxyInstance(PlayerProfile.class.getClassLoader(),
                new Class<?>[] {PlayerProfile.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getId" -> who;
                    case "getName" -> "Steve";
                    default -> TestSupport.defaultValue(method.getReturnType());
                });
        PlayerLoginConnection connection = (PlayerLoginConnection) Proxy.newProxyInstance(PlayerLoginConnection.class.getClassLoader(),
                new Class<?>[] {PlayerLoginConnection.class}, (proxy, method, args) -> TestSupport.defaultValue(method.getReturnType()));
        InetAddress loopback = InetAddress.getLoopbackAddress();
        AsyncPlayerPreLoginEvent event = new AsyncPlayerPreLoginEvent("Steve", loopback, new InetSocketAddress(loopback, 25565),
                who, false, profile, "localhost", connection);
        listener.onPreLogin(event);
        return event;
    }

    static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    /** A player with a recorder, a kick log and an online flag, so tests can see who was kicked and who heard what. */
    static final class TestPlayer {

        final TestSupport.Recorder rec = new TestSupport.Recorder();
        final List<Component> kicks = new ArrayList<>();
        final UUID id = UUID.randomUUID();
        final String name;
        boolean online = true;
        final Player player;

        TestPlayer(String name, String... permissions) {
            this.name = name;
            rec.permissions.addAll(List.of(permissions));
            this.player = (Player) Proxy.newProxyInstance(TestPlayer.class.getClassLoader(), new Class<?>[] {Player.class},
                    (proxy, method, args) -> handle(proxy, method, args));
        }

        private Object handle(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "hasPermission" -> {
                    return rec.permissions.contains((String) args[0]);
                }
                case "sendMessage" -> {
                    if (args[0] instanceof Component c) {
                        rec.messages.add(c);
                    }
                    return null;
                }
                case "playSound" -> {
                    if (args[0] instanceof Sound s) {
                        rec.sounds.add(s);
                    }
                    return null;
                }
                case "kick" -> {
                    kicks.add((Component) args[0]);
                    online = false;
                    return null;
                }
                case "isOnline" -> {
                    return online;
                }
                case "getUniqueId" -> {
                    return id;
                }
                case "getName" -> {
                    return name;
                }
                case "hashCode" -> {
                    return System.identityHashCode(proxy);
                }
                case "equals" -> {
                    return proxy == args[0];
                }
                case "toString" -> {
                    return "TestPlayer(" + name + ")";
                }
                default -> {
                    return TestSupport.defaultValue(method.getReturnType());
                }
            }
        }
    }
}
