package dev.casperrs.cookieCore.sound;

import net.kyori.adventure.key.InvalidKeyException;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.random.RandomGenerator;

/**
 * The <b>only</b> place in the plugin that plays sounds. Plays events by name from {@code sounds.yml}. Unknown events
 * are logged once and skipped. Sounds go to one player at a time. Main thread only.
 *
 * <p>Players for whom {@code usesCustomSounds} is true hear the event's {@code key}; everybody else its vanilla
 * {@code fallback}. Core ships no resource pack yet, so the plugin passes "nobody" and every player hears the fallback.
 */
public final class SoundService {

    private final Logger log;
    private final RandomGenerator random;
    private final Predicate<Player> usesCustomSounds;
    private final Set<String> warned = new HashSet<>();
    private Map<String, SoundDef> sounds = Map.of();

    public SoundService(Logger log, RandomGenerator random, Predicate<Player> usesCustomSounds) {
        this.log = log;
        this.random = random;
        this.usesCustomSounds = usesCustomSounds;
    }

    /** Swap in a freshly parsed {@code sounds.yml} (enable / reload). */
    public void load(Map<String, SoundDef> sounds) {
        this.sounds = Map.copyOf(sounds);
        warned.clear();
    }

    public boolean has(String event) {
        return sounds.containsKey(event);
    }

    /** Plays {@code event} to {@code player}, at the player. */
    public void play(Player player, String event) {
        Sound sound = resolve(player, event);
        if (sound != null) {
            player.playSound(sound, Sound.Emitter.self());
        }
    }

    /** Plays {@code event} to {@code sender} if it is a player; the console hears nothing. */
    public void play(CommandSender sender, String event) {
        if (sender instanceof Player player) {
            play(player, event);
        }
    }

    private @Nullable Sound resolve(Player player, String event) {
        SoundDef def = sounds.get(event);
        if (def == null) {
            warnOnce(event, "Unknown sound event '" + event + "' (not in sounds.yml); skipped");
            return null;
        }
        String raw = usesCustomSounds.test(player) ? def.key() : def.fallback();
        Key key;
        try {
            key = Key.key(raw);
        } catch (InvalidKeyException e) {
            warnOnce(event, "sounds.yml: '" + event + "' has an invalid key '" + raw + "'; skipped");
            return null;
        }
        float pitch = def.pitchMax() > def.pitchMin()
                ? def.pitchMin() + random.nextFloat() * (def.pitchMax() - def.pitchMin())
                : def.pitchMin();
        return Sound.sound(key, source(def.source()), def.volume(), pitch);
    }

    private void warnOnce(String event, String message) {
        if (warned.add(event)) {
            log.warning(message);
        }
    }

    static Sound.Source source(String name) {
        String normalized = switch (name) {
            case "records" -> "record";
            case "blocks" -> "block";
            case "players" -> "player";
            default -> name;
        };
        try {
            return Sound.Source.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Sound.Source.MASTER;
        }
    }
}
