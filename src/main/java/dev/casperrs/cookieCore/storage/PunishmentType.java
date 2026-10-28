package dev.casperrs.cookieCore.storage;

/** The {@code type} column of {@code punishments}. */
public enum PunishmentType {
    /** One-off removal; stored inactive, since there is nothing to enforce or undo. */
    KICK(false),
    /** Permanent when {@code expires_at} is empty; stays active after it expires, so there is no cleanup task. */
    BAN(true),
    /** Blocks chat; permanent when {@code expires_at} is empty, expiry works like a tempban. */
    MUTE(true),
    /** One-off like a kick; only shows in the history. */
    WARN(false);

    private final boolean activeOnInsert;

    PunishmentType(boolean activeOnInsert) {
        this.activeOnInsert = activeOnInsert;
    }

    boolean activeOnInsert() {
        return activeOnInsert;
    }
}
