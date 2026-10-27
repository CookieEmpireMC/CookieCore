package dev.casperrs.cookieCore.text;

import dev.casperrs.cookieCore.storage.BanRecord;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerKickEvent;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * All player-facing text, from {@code messages.yml} as MiniMessage (one shared {@link MiniMessage}).
 * Dynamic values go in as placeholders; anything typed by players only via {@link Placeholder#unparsed}.
 * The only place that turns text from the config into components, so no message literal lives in Java code, and the
 * only place that disconnects a player with a screen. Reads are safe from any thread (the login check runs off the main
 * thread); {@link #load} may be called from the main thread while they run.
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
    private final Set<String> warned = ConcurrentHashMap.newKeySet();
    private volatile ConfigurationSection yaml = new YamlConfiguration();

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

    /** The screen of a ban or tempban; the time left is counted from {@code nowMillis}. */
    public Component banScreen(BanRecord ban, long nowMillis) {
        TagResolver who = TagResolver.resolver(Placeholder.unparsed("staff", ban.actorName()),
                Placeholder.unparsed("reason", ban.reason()), Placeholder.unparsed("id", String.valueOf(ban.id())));
        Long end = ban.expiresAt();
        if (end == null) {
            return component("moderation.screen.ban", who);
        }
        return component("moderation.screen.tempban", who,
                Placeholder.unparsed("remaining", TimeText.remaining(Duration.ofMillis(end - nowMillis))),
                Placeholder.unparsed("until", TimeText.until(end)));
    }

    public Component kickScreen(String staff, String reason) {
        return component("moderation.screen.kick", Placeholder.unparsed("staff", staff), Placeholder.unparsed("reason", reason));
    }

    /** Disconnects an online player with {@code screen}. Main thread. {@code cause} is {@code BANNED} or {@code KICKED}, never the deprecated {@code KICK_COMMAND}. */
    public void disconnect(Player player, Component screen, PlayerKickEvent.Cause cause) {
        player.kick(screen, cause);
    }

    /** Login: refuse a banned player. Any thread. */
    public void denyBanned(AsyncPlayerPreLoginEvent event, BanRecord ban, long nowMillis) {
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, banScreen(ban, nowMillis));
    }

    /** Login: the active bans are not loaded yet, so nobody can be checked. Any thread. */
    public void denyStarting(AsyncPlayerPreLoginEvent event) {
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, component("moderation.starting"));
    }

    /** {@code <prefix>} for replies. */
    private TagResolver prefix() {
        return Placeholder.parsed("prefix", yaml.getString("prefix", ""));
    }
}
