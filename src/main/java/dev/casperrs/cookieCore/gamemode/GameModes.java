package dev.casperrs.cookieCore.gamemode;

import dev.casperrs.cookieCore.Perm;
import dev.casperrs.cookieCore.text.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.GameMode;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static java.util.Map.entry;

/** Pure lookups for the four game modes: command tokens, permission nodes and message keys. */
public final class GameModes {

    public static final List<GameMode> ALL = List.of(GameMode.SURVIVAL, GameMode.CREATIVE, GameMode.ADVENTURE, GameMode.SPECTATOR);

    // GameMode#getValue and #getByValue are internal API, so the numbers are mapped here
    private static final Map<String, GameMode> BY_TOKEN = Map.ofEntries(
            entry("survival", GameMode.SURVIVAL), entry("s", GameMode.SURVIVAL), entry("0", GameMode.SURVIVAL),
            entry("creative", GameMode.CREATIVE), entry("c", GameMode.CREATIVE), entry("1", GameMode.CREATIVE),
            entry("adventure", GameMode.ADVENTURE), entry("a", GameMode.ADVENTURE), entry("2", GameMode.ADVENTURE),
            entry("spectator", GameMode.SPECTATOR), entry("sp", GameMode.SPECTATOR), entry("3", GameMode.SPECTATOR));

    private GameModes() {
    }

    /** Full name, 0 to 3 or s/c/a/sp, in any case. */
    public static Optional<GameMode> parse(String token) {
        return Optional.ofNullable(BY_TOKEN.get(token.toLowerCase(Locale.ROOT)));
    }

    public static String permission(GameMode mode) {
        return switch (mode) {
            case SURVIVAL -> Perm.GAMEMODE_SURVIVAL;
            case CREATIVE -> Perm.GAMEMODE_CREATIVE;
            case ADVENTURE -> Perm.GAMEMODE_ADVENTURE;
            case SPECTATOR -> Perm.GAMEMODE_SPECTATOR;
        };
    }

    public static String name(GameMode mode) {
        return mode.name().toLowerCase(Locale.ROOT);
    }

    /** True for the modes in which the server itself allows flying. */
    public static boolean flies(GameMode mode) {
        return mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR;
    }

    /** The {@code <mode>} placeholder: the mode's display name from messages.yml. */
    public static TagResolver placeholder(Messages messages, GameMode mode) {
        return Placeholder.component("mode", messages.component(nameKey(mode)));
    }

    private static String nameKey(GameMode mode) {
        return switch (mode) {
            case SURVIVAL -> "gamemode.mode.survival";
            case CREATIVE -> "gamemode.mode.creative";
            case ADVENTURE -> "gamemode.mode.adventure";
            case SPECTATOR -> "gamemode.mode.spectator";
        };
    }
}
