package dev.casperrs.cookieCore;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import dev.casperrs.cookieCore.storage.PlayerRecord;
import dev.casperrs.cookieCore.storage.PlayerStore;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/** Server-free stand-ins for the few Bukkit types the code under test touches (java.lang.reflect.Proxy). */
public final class TestSupport {

    private TestSupport() {
    }

    /** A sender that records what it was sent and what permissions it has. */
    public static final class Recorder {
        public final List<Component> messages = new ArrayList<>();
        public final List<Sound> sounds = new ArrayList<>();
        public final Set<String> permissions = new HashSet<>();

        public List<String> plain() {
            return messages.stream().map(PlainTextComponentSerializer.plainText()::serialize).toList();
        }

        public String lastPlain() {
            return plain().getLast();
        }
    }

    public static CommandSender console(Recorder rec, String... permissions) {
        Collections.addAll(rec.permissions, permissions);
        return proxy(CommandSender.class, rec, UUID.randomUUID());
    }

    public static Player player(Recorder rec, String... permissions) {
        Collections.addAll(rec.permissions, permissions);
        return proxy(Player.class, rec, UUID.randomUUID());
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Recorder rec, UUID id) {
        InvocationHandler handler = (proxy, method, args) -> {
            switch (method.getName()) {
                case "hasPermission" -> {
                    if (args != null && args[0] instanceof String s) {
                        return rec.permissions.contains(s);
                    }
                }
                case "sendMessage" -> {
                    if (args != null && args.length >= 1 && args[0] instanceof Component c) {
                        rec.messages.add(c);
                        return null;
                    }
                }
                case "playSound" -> {
                    if (args != null && args.length >= 1 && args[0] instanceof Sound s) {
                        rec.sounds.add(s);
                        return null;
                    }
                }
                case "getUniqueId" -> {
                    return id;
                }
                case "getName" -> {
                    return "tester";
                }
                case "hashCode" -> {
                    return System.identityHashCode(proxy);
                }
                case "equals" -> {
                    return proxy == args[0];
                }
                case "toString" -> {
                    return type.getSimpleName() + "Proxy";
                }
                default -> {
                }
            }
            return defaultValue(method.getReturnType());
        };
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
    }

    /** A PlayerDirectory store that holds nothing and never fails. */
    public static PlayerStore noStore() {
        return new PlayerStore() {
            @Override
            public void init() {
            }

            @Override
            public void upsertSeen(UUID uuid, String name, long now) {
            }

            @Override
            public List<PlayerRecord> loadAll() {
                return List.of();
            }

            @Override
            public Optional<PlayerRecord> findByName(String name) {
                return Optional.empty();
            }

            @Override
            public void close() {
            }
        };
    }

    /** A command source whose sender is {@code sender}. */
    public static io.papermc.paper.command.brigadier.CommandSourceStack source(CommandSender sender) {
        return source(sender, sender instanceof Entity entity ? entity : null);
    }

    /** A command source whose sender is {@code sender} and whose executor (what {@code /execute as} changes) is {@code executor}. */
    public static io.papermc.paper.command.brigadier.CommandSourceStack source(CommandSender sender, Entity executor) {
        return (io.papermc.paper.command.brigadier.CommandSourceStack) Proxy.newProxyInstance(
                TestSupport.class.getClassLoader(),
                new Class<?>[] {io.papermc.paper.command.brigadier.CommandSourceStack.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getSender" -> sender;
                    case "getExecutor" -> executor;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "CommandSourceStackProxy";
                    default -> defaultValue(method.getReturnType());
                });
    }

    /** The value a Java proxy must return for {@code type}: null for objects, zero or false for primitives. */
    public static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0f;
        }
        return 0d;
    }

    /** A logger that collects its records and prints nothing. */
    public static final class LogCapture {
        public final Logger logger = Logger.getAnonymousLogger();
        public final List<LogRecord> records = new ArrayList<>();

        public LogCapture() {
            logger.setUseParentHandlers(false);
            logger.addHandler(new Handler() {
                @Override
                public void publish(LogRecord record) {
                    synchronized (records) {
                        records.add(record);
                    }
                }

                @Override
                public void flush() {
                }

                @Override
                public void close() {
                }
            });
        }

        public synchronized List<String> messages() {
            synchronized (records) {
                return records.stream().map(LogRecord::getMessage).toList();
            }
        }
    }

    /** A YAML file from the plugin's resources (as packaged in the jar). */
    public static YamlConfiguration resourceYaml(String name) {
        try (InputStream in = TestSupport.class.getResourceAsStream("/" + name)) {
            if (in == null) {
                throw new IllegalStateException("resource " + name + " not on the classpath");
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return YamlConfiguration.loadConfiguration(reader);
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static YamlConfiguration yaml(String text) {
        YamlConfiguration y = new YamlConfiguration();
        try {
            y.loadFromString(text);
        } catch (org.bukkit.configuration.InvalidConfigurationException e) {
            throw new IllegalArgumentException(e);
        }
        return y;
    }
}
