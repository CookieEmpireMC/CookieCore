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
    public static final String SCOREBOARD_TOGGLE = "cookiecore.scoreboard.toggle";
    public static final String GAMEMODE_SURVIVAL = "cookiecore.gamemode.survival";
    public static final String GAMEMODE_CREATIVE = "cookiecore.gamemode.creative";
    public static final String GAMEMODE_ADVENTURE = "cookiecore.gamemode.adventure";
    public static final String GAMEMODE_SPECTATOR = "cookiecore.gamemode.spectator";
    public static final String GAMEMODE_OTHERS = "cookiecore.gamemode.others";
    public static final String FLY = "cookiecore.fly";
    public static final String FLY_OTHERS = "cookiecore.fly.others";
    public static final String SPEED = "cookiecore.speed";
    public static final String SPEED_OTHERS = "cookiecore.speed.others";
    public static final String STAFF_KICK = "cookiecore.staff.kick";
    /** Covers {@code /ban} and {@code /tempban}. */
    public static final String STAFF_BAN = "cookiecore.staff.ban";
    public static final String STAFF_UNBAN = "cookiecore.staff.unban";
    /** Covers {@code /mute} and {@code /tempmute}. */
    public static final String STAFF_MUTE = "cookiecore.staff.mute";
    public static final String STAFF_UNMUTE = "cookiecore.staff.unmute";
    /** Sees the alerts of silent ({@code -s}) actions. */
    public static final String STAFF_NOTIFY = "cookiecore.staff.notify";

    /** Every node above; used by tests to keep {@code paper-plugin.yml} in sync. */
    public static final List<String> ALL = List.of(ADMIN_VERSION, ADMIN_RELOAD, SCOREBOARD_TOGGLE,
            GAMEMODE_SURVIVAL, GAMEMODE_CREATIVE, GAMEMODE_ADVENTURE, GAMEMODE_SPECTATOR, GAMEMODE_OTHERS,
            FLY, FLY_OTHERS, SPEED, SPEED_OTHERS, STAFF_KICK, STAFF_BAN, STAFF_UNBAN, STAFF_MUTE, STAFF_UNMUTE, STAFF_NOTIFY);

    private Perm() {
    }
}
