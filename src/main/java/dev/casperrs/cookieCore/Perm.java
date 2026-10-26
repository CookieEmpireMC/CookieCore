package dev.casperrs.cookieCore;

import java.util.List;

/**
 * Every permission node of the plugin, as plain Bukkit permissions ({@code cookiecore.<area>.<action>}) so that
 * LuckPerms (or any permission plugin) works without a dependency. Each constant must also be declared in
 * {@code paper-plugin.yml}. Add new nodes here first.
 */
public final class Perm {

    public static final String ADMIN_VERSION = "cookiecore.admin.version";
    public static final String ADMIN_RELOAD = "cookiecore.admin.reload";
    public static final String GAMEMODE_SURVIVAL = "cookiecore.gamemode.survival";
    public static final String GAMEMODE_CREATIVE = "cookiecore.gamemode.creative";
    public static final String GAMEMODE_ADVENTURE = "cookiecore.gamemode.adventure";
    public static final String GAMEMODE_SPECTATOR = "cookiecore.gamemode.spectator";
    public static final String GAMEMODE_OTHERS = "cookiecore.gamemode.others";
    public static final String FLY = "cookiecore.fly";
    public static final String FLY_OTHERS = "cookiecore.fly.others";
    public static final String SPEED = "cookiecore.speed";
    public static final String SPEED_OTHERS = "cookiecore.speed.others";

    /** Every node above; used by tests to keep {@code paper-plugin.yml} in sync. */
    public static final List<String> ALL = List.of(ADMIN_VERSION, ADMIN_RELOAD,
            GAMEMODE_SURVIVAL, GAMEMODE_CREATIVE, GAMEMODE_ADVENTURE, GAMEMODE_SPECTATOR, GAMEMODE_OTHERS,
            FLY, FLY_OTHERS, SPEED, SPEED_OTHERS);

    private Perm() {
    }
}
