package dev.casperrs.cookieCore.sound;

/**
 * One event from {@code sounds.yml}.
 *
 * @param key      sound for players who use custom sounds (a pack key, or vanilla while Core ships no pack)
 * @param fallback vanilla sound used otherwise
 * @param source   lower-case sound source name ({@code master}, {@code ui}, {@code player}, …)
 */
public record SoundDef(String event, String key, String fallback, float volume,
                       float pitchMin, float pitchMax, String source) {
}
