package dev.casperrs.cookieCore.gamemode;

/**
 * The 1 to 10 speed scale of {@code /speed}. 1 is the vanilla default, 10 the largest value the API accepts, linear
 * in between. Bukkit's walk and fly speeds are -1..1 with different vanilla defaults (walk 0.2, fly 0.1).
 */
public final class SpeedScale {

    public static final int MIN_LEVEL = 1;
    public static final int MAX_LEVEL = 10;
    public static final float WALK_DEFAULT = 0.2f;
    public static final float FLY_DEFAULT = 0.1f;
    private static final float MAX_SPEED = 1.0f;

    private SpeedScale() {
    }

    public static boolean valid(int level) {
        return level >= MIN_LEVEL && level <= MAX_LEVEL;
    }

    /** Levels outside 1..10 are clamped. */
    public static float walk(int level) {
        return scale(level, WALK_DEFAULT);
    }

    /** Levels outside 1..10 are clamped. */
    public static float fly(int level) {
        return scale(level, FLY_DEFAULT);
    }

    private static float scale(int level, float normal) {
        int clamped = Math.clamp(level, MIN_LEVEL, MAX_LEVEL);
        double fraction = (clamped - MIN_LEVEL) / (double) (MAX_LEVEL - MIN_LEVEL);
        return (float) Math.min(MAX_SPEED, normal + (MAX_SPEED - normal) * fraction);
    }
}
