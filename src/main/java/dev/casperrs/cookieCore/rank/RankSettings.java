package dev.casperrs.cookieCore.rank;

import dev.casperrs.cookieCore.text.ChatText;
import net.kyori.adventure.text.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** The {@code ranks} and {@code default-prefix} of {@code chat.yml}, parsed. Immutable. */
public record RankSettings(Component defaultPrefix, List<Rank> ranks) {

    private static final String FILE = "chat.yml";

    /** What a rank gives a player: the prefix and the position in the tab list. */
    public record Resolved(Optional<Component> prefix, int order) {
    }

    /** A player with {@code permission} gets {@code prefix} in front of their name. */
    public record Rank(String permission, Component prefix) {
    }

    /** Problems found are reported and the entry skipped; never fails. */
    public static RankSettings parse(Map<String, Object> yaml, Consumer<String> problems) {
        Component defaultPrefix = yaml.get("default-prefix") instanceof String s && !s.isBlank() ? ChatText.mini(s) : Component.empty();
        return new RankSettings(defaultPrefix, ranks(yaml.get("ranks"), problems));
    }

    /** The first rank whose permission the player has, else the default prefix; empty when that is empty too. */
    public Optional<Component> prefixFor(Predicate<String> hasPermission) {
        return resolve(hasPermission).prefix();
    }

    /**
     * The first rank the player has gives {@code ranks.size() - index} (the first listed is the biggest, higher is listed
     * first in the tab list); no rank gives 0.
     */
    public Resolved resolve(Predicate<String> hasPermission) {
        for (int i = 0; i < ranks.size(); i++) {
            if (hasPermission.test(ranks.get(i).permission())) {
                return new Resolved(Optional.of(ranks.get(i).prefix()), ranks.size() - i);
            }
        }
        return new Resolved(defaultPrefix.equals(Component.empty()) ? Optional.empty() : Optional.of(defaultPrefix), 0);
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
}
