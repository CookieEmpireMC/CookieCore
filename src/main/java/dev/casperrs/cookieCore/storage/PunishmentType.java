package dev.casperrs.cookieCore.storage;

/** The {@code type} column of {@code punishments}. Phase 4b adds the mute and warning types. */
public enum PunishmentType {
    /** One-off removal; stored inactive, since there is nothing to enforce or undo. */
    KICK(false),
    /** Permanent when {@code expires_at} is empty; stays active after it expires, so there is no cleanup task. */
    BAN(true);

    private final boolean activeOnInsert;

    PunishmentType(boolean activeOnInsert) {
        this.activeOnInsert = activeOnInsert;
    }

    boolean activeOnInsert() {
        return activeOnInsert;
    }
}
