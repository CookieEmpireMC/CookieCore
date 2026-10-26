package dev.casperrs.cookieCore.text;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * All player-facing text, from {@code messages.yml} as MiniMessage (one shared {@link MiniMessage}).
 * Dynamic values go in as placeholders; anything typed by players only via {@link Placeholder#unparsed}.
 * The only place that turns text from the config into components, so no message literal lives in Java code.
 * Main thread only (reload swaps the file).
 */
public final class Messages {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    /** Keys the plugin itself uses; {@link #problems} reports the ones a messages.yml lacks. */
    public static final List<String> REQUIRED_KEYS = List.of(
            "prefix",
            "command.usage",
            "command.no-permission",
            "command.unknown-player",
            "command.invalid-duration",
            "command.reloaded",
            "command.reload-failed",
            "command.reload-problem",
            "command.version",
            "command.storage-up",
            "command.storage-down",
            "scoreboard.shown",
            "scoreboard.hidden",
            "scoreboard.disabled",
            "scoreboard.players-only");

    private final Logger log;
    private final Set<String> warned = new HashSet<>();
    private ConfigurationSection yaml = new YamlConfiguration();

    public Messages(Logger log) {
        this.log = log;
    }

    public static MiniMessage miniMessage() {
        return MM;
    }

    /** Swap in a freshly loaded {@code messages.yml} (with the jar copy as defaults). */
    public void load(ConfigurationSection messages) {
        this.yaml = messages;
        warned.clear();
    }

    /**
     * Required keys that {@code messages} does not itself define as text; defaults set on it (the jar copy) do not
     * count, but they still cover the key at runtime.
     */
    public static List<String> problems(ConfigurationSection messages) {
        List<String> out = new ArrayList<>();
        for (String key : REQUIRED_KEYS) {
            if (!messages.isSet(key) || !messages.isString(key)) {
                out.add("messages.yml: '" + key + "' is missing or not text; using the built-in default");
            }
        }
        return out;
    }

    /** Raw MiniMessage string at {@code path}; the path itself when missing (logged once). */
    public String raw(String path) {
        String s = yaml.getString(path);
        if (s == null) {
            if (warned.add(path)) {
                log.warning("messages.yml is missing '" + path + "'");
            }
            return path;
        }
        return s;
    }

    public boolean has(String path) {
        return yaml.isString(path);
    }

    /** The message at {@code path} with {@code <prefix>} and the given placeholders filled in. */
    public Component component(String path, TagResolver... resolvers) {
        return MM.deserialize(raw(path), TagResolver.resolver(prefix(), TagResolver.resolver(resolvers)));
    }

    public void send(Audience to, String path, TagResolver... resolvers) {
        to.sendMessage(component(path, resolvers));
    }

    /** {@code <prefix>} for replies. */
    private TagResolver prefix() {
        return Placeholder.parsed("prefix", yaml.getString("prefix", ""));
    }
}
