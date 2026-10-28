package dev.casperrs.cookieCore.chat;

import dev.casperrs.cookieCore.text.ChatText;
import dev.casperrs.cookieCore.text.Template;
import net.kyori.adventure.text.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * {@code chat.yml} parsed and compiled. Immutable: the chat and the server-list ping read it on other threads through a
 * volatile reference, so a reload swaps the whole object.
 */
public record ChatSettings(Template format, Template staffFormat, Component defaultPrefix, List<Rank> ranks,
                           @Nullable Motd motd, Welcome welcome) {

    /** Placeholders of {@code format} and {@code staff-format}. The message comes last so nothing replaces text inside it. */
    static final List<String> CHAT_KEYS = List.of("server", "rank_prefix", "player", "message");
    static final List<String> MOTD_KEYS = List.of("server", "online", "max");
    static final int DEFAULT_FADE_IN = 10;
    static final int DEFAULT_STAY = 70;
    static final int DEFAULT_FADE_OUT = 20;
    private static final String FILE = "chat.yml";

    /** A player with {@code permission} gets {@code prefix} in front of their name. */
    public record Rank(String permission, Component prefix) {
    }

    /** The server-list text; {@code second} is null when the file has one line. */
    public record Motd(Template first, @Nullable Template second) {
    }

    /** Title fade times in ticks. */
    public record Welcome(int fadeInTicks, int stayTicks, int fadeOutTicks) {
    }

    /**
     * Empty when {@code format} or {@code staff-format} is missing or has no {@code <message>}; every problem found is
     * reported, so the admin fixes the file in one go. Smaller faults (a bad rank entry, a negative time) are reported and
     * replaced by a default.
     */
    public static Optional<ChatSettings> parse(Map<String, Object> yaml, Consumer<String> problems) {
        Template format = template(yaml, "format", problems);
        Template staffFormat = template(yaml, "staff-format", problems);
        if (format == null || staffFormat == null) {
            return Optional.empty();
        }
        Component defaultPrefix = yaml.get("default-prefix") instanceof String s && !s.isBlank() ? ChatText.mini(s) : Component.empty();
        return Optional.of(new ChatSettings(format, staffFormat, defaultPrefix, ranks(yaml.get("ranks"), problems),
                motd(yaml.get("motd"), problems), welcome(yaml.get("welcome"), problems)));
    }

    /** The first rank whose permission the player has, else the default prefix; empty when that is empty too. */
    public Optional<Component> prefixFor(Predicate<String> hasPermission) {
        for (Rank rank : ranks) {
            if (hasPermission.test(rank.permission())) {
                return Optional.of(rank.prefix());
            }
        }
        return defaultPrefix.equals(Component.empty()) ? Optional.empty() : Optional.of(defaultPrefix);
    }

    private static @Nullable Template template(Map<String, Object> yaml, String key, Consumer<String> problems) {
        if (!(yaml.get(key) instanceof String text) || text.isBlank()) {
            problems.accept(FILE + ": '" + key + "' is missing or empty");
            return null;
        }
        Template template = Template.compile(text, CHAT_KEYS);
        if (!template.keys().contains("message")) {
            problems.accept(FILE + ": '" + key + "' must contain <message>");
            return null;
        }
        return template;
    }

    private static List<Rank> ranks(@Nullable Object raw, Consumer<String> problems) {
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof List<?> entries)) {
            problems.accept(FILE + ": 'ranks' must be a list of permission and prefix pairs; no ranks used");
            return List.of();
        }
        List<Rank> out = new ArrayList<>();
        int number = 0;
        for (Object entry : entries) {
            number++;
            if (entry instanceof Map<?, ?> map && map.get("permission") instanceof String permission && !permission.isBlank()
                    && map.get("prefix") instanceof String prefix) {
                out.add(new Rank(permission.trim(), ChatText.mini(prefix)));
            } else {
                problems.accept(FILE + ": ranks entry " + number + " needs a 'permission' and a 'prefix'; skipped");
            }
        }
        return List.copyOf(out);
    }

    private static @Nullable Motd motd(@Nullable Object raw, Consumer<String> problems) {
        if (raw == null) {
            return null;
        }
        if (!(raw instanceof List<?> lines) || lines.isEmpty() || lines.size() > 2) {
            problems.accept(FILE + ": 'motd' must be a list of one or two lines; the default server-list text is kept");
            return null;
        }
        for (Object line : lines) {
            if (!(line instanceof String text) || text.isBlank()) {
                problems.accept(FILE + ": every 'motd' line must be text; the default server-list text is kept");
                return null;
            }
        }
        Template first = Template.compile((String) lines.get(0), MOTD_KEYS);
        Template second = lines.size() > 1 ? Template.compile((String) lines.get(1), MOTD_KEYS) : null;
        return new Motd(first, second);
    }

    private static Welcome welcome(@Nullable Object raw, Consumer<String> problems) {
        if (!(raw instanceof Map<?, ?> map)) {
            return new Welcome(DEFAULT_FADE_IN, DEFAULT_STAY, DEFAULT_FADE_OUT);
        }
        return new Welcome(ticks(map, "fade-in", DEFAULT_FADE_IN, problems), ticks(map, "stay", DEFAULT_STAY, problems),
                ticks(map, "fade-out", DEFAULT_FADE_OUT, problems));
    }

    private static int ticks(Map<?, ?> map, String key, int fallback, Consumer<String> problems) {
        Object raw = map.get(key);
        if (raw == null) {
            return fallback;
        }
        if (raw instanceof Integer n && n >= 0) {
            return n;
        }
        problems.accept(FILE + ": welcome." + key + " '" + raw + "' is not a whole number of ticks (0 or more); using " + fallback);
        return fallback;
    }
}
