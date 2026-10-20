package dev.casperrs.cookieCore;

import java.util.List;

/**
 * Every permission node of the plugin, as plain Bukkit permissions ({@code cookiecore.<area>.<action>}) so that
 * LuckPerms (or any permission plugin) works without a dependency. Each constant must also be declared in
 * {@code plugin.yml}. Add new nodes here first.
 */
public final class Perm {

    public static final String ADMIN_VERSION = "cookiecore.admin.version";
    public static final String ADMIN_RELOAD = "cookiecore.admin.reload";

    /** Every node above; used by tests to keep {@code plugin.yml} in sync. */
    public static final List<String> ALL = List.of(ADMIN_VERSION, ADMIN_RELOAD);

    private Perm() {
    }
}
