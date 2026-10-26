package dev.casperrs.cookieCore.sound;

/**
 * One event from {@code sounds.yml}. {@code key} is the sound for players who use custom sounds (a pack key, or vanilla
 * while Core ships no pack), {@code fallback} the vanilla sound used otherwise, {@code source} a lower-case sound source
 * name ({@code master}, {@code ui}, {@code player}, …).
 */
public record SoundDef(String event, String key, String fallback, float volume,
                       float pitchMin, float pitchMax, String source) {
}
