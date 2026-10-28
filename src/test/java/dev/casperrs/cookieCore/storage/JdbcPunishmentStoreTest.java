package dev.casperrs.cookieCore.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.casperrs.cookieCore.TestSupport;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JdbcPunishmentStoreTest {

    private final H2 db = new H2();
    private final TestSupport.LogCapture log = new TestSupport.LogCapture();
    private final JdbcPlayerStore store = new JdbcPlayerStore(db.factory(), "core_", log.logger);
    private final JdbcPunishmentStore punishments = new JdbcPunishmentStore(store, log.logger);
    private final UUID a = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private final UUID b = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    @Test
    void punishmentRowsGetGeneratedIdsKicksStayInactiveAndOnlyBansLoad() throws Exception {
        store.init();
        UUID staff = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        long kick = punishments.insert(new NewPunishment(PunishmentType.KICK, a, "Alice", null, "Console", "spam", 100, null, true));
        long ban = punishments.insert(new NewPunishment(PunishmentType.BAN, b, "Bob", staff, "Alex", "griefing", 200, null, false));
        long tempban = punishments.insert(new NewPunishment(PunishmentType.BAN, c, "Carl", staff, "Alex", "spam", 300, 5_000L, false));
        assertTrue(kick < ban && ban < tempban, "ids are generated in order");
        List<BanRecord> active = punishments.loadActiveBans(1_000);
        assertEquals(List.of(ban, tempban), active.stream().map(BanRecord::id).toList());
        assertNull(active.getFirst().expiresAt());
        assertEquals(Long.valueOf(5_000L), active.getLast().expiresAt());
        assertEquals(1, db.count("core_punishments WHERE active = FALSE AND type = 'KICK'"));
    }

    @Test
    void unbanDeactivatesEveryActiveRowOfThePlayerAndRecordsWhoDidIt() throws Exception {
        store.init();
        punishments.insert(new NewPunishment(PunishmentType.BAN, a, "Alice", null, "Console", "old", 100, 500L, false));
        punishments.insert(new NewPunishment(PunishmentType.BAN, a, "Alice", null, "Console", "perm", 200, null, false));
        punishments.insert(new NewPunishment(PunishmentType.BAN, b, "Bob", null, "Console", "perm", 200, null, false));
        assertEquals(2, punishments.deactivateBans(a, UUID.randomUUID(), "Alex", "appealed", 1_000));
        assertEquals(List.of(b), punishments.loadActiveBans(1_000).stream().map(BanRecord::targetUuid).toList());
        assertEquals(2, db.count("core_punishments WHERE removed_by_name = 'Alex' AND removed_at = 1000"));
    }

    @Test
    void aCorruptPunishmentRowIsSkippedAndTheOthersLoad() throws Exception {
        store.init();
        punishments.insert(new NewPunishment(PunishmentType.BAN, b, "Bob", null, "Console", "ok", 1, null, false));
        db.execute("INSERT INTO core_punishments (type, target_uuid, target_name, actor_uuid, actor_name, reason, created_at, "
                + "expires_at, silent, active) VALUES ('BAN', 'not-a-uuid', 'Bad', NULL, 'Console', 'x', 1, NULL, FALSE, TRUE)");
        assertEquals(List.of(b), punishments.loadActiveBans(0).stream().map(BanRecord::targetUuid).toList());
        assertTrue(log.messages().stream().anyMatch(m -> m.contains("Skipped 1 row(s) in core_punishments")), log.messages().toString());
    }

    @Test
    void mutesLoadAndUndoSeparatelyFromBans() throws Exception {
        store.init();
        punishments.insert(new NewPunishment(PunishmentType.BAN, a, "Alice", null, "Console", "ban", 100, null, false));
        punishments.insert(new NewPunishment(PunishmentType.MUTE, a, "Alice", null, "Console", "expired", 100, 500L, false));
        punishments.insert(new NewPunishment(PunishmentType.MUTE, a, "Alice", null, "Console", "perm", 200, null, false));
        punishments.insert(new NewPunishment(PunishmentType.MUTE, b, "Bob", null, "Console", "perm", 200, null, false));
        List<MuteRecord> mutes = punishments.loadActiveMutes(1_000);
        assertEquals(2, mutes.size());
        assertEquals(Set.of(a, b), Set.copyOf(mutes.stream().map(MuteRecord::targetUuid).toList()));

        assertEquals(2, punishments.deactivateMutes(a, UUID.randomUUID(), "Alex", "appealed", 1_000), "the expired row counts too, like an unban");
        assertEquals(List.of(b), punishments.loadActiveMutes(1_000).stream().map(MuteRecord::targetUuid).toList());
        assertEquals(List.of(a), punishments.loadActiveBans(1_000).stream().map(BanRecord::targetUuid).toList());
    }

    /** Newest first (id breaks a tie), two rows per page, bans, mutes, warns and kicks in one list, other players excluded. */
    @Test
    void historyIsNewestFirstPagesWithLimitAndOffsetAndMixesEveryType() throws Exception {
        store.init();
        long w1 = punishments.insert(new NewPunishment(PunishmentType.WARN, a, "Alice", null, "Console", "w1", 100, null, false));
        long kick = punishments.insert(new NewPunishment(PunishmentType.KICK, a, "Alice", null, "Console", "kick", 150, null, false));
        long ban = punishments.insert(new NewPunishment(PunishmentType.BAN, a, "Alice", null, "Console", "ban", 200, null, false));
        long mute = punishments.insert(new NewPunishment(PunishmentType.MUTE, a, "Alice", null, "Console", "mute", 250, null, false));
        long warn = punishments.insert(new NewPunishment(PunishmentType.WARN, a, "Alice", null, "Console", "w2", 300, null, false));
        long tie = punishments.insert(new NewPunishment(PunishmentType.WARN, a, "Alice", null, "Console", "w3", 300, null, false));
        punishments.insert(new NewPunishment(PunishmentType.BAN, b, "Bob", null, "Console", "other", 250, null, false));

        assertEquals(List.of(tie, warn), ids(punishments.history(a, 2, 0)));
        assertEquals(List.of(mute, ban), ids(punishments.history(a, 2, 2)));
        assertEquals(List.of(kick, w1), ids(punishments.history(a, 2, 4)));
        assertEquals(List.of(), punishments.history(a, 2, 6));

        List<HistoryEntry> all = punishments.history(a, 10, 0);
        assertEquals(List.of(tie, warn, mute, ban, kick, w1), ids(all));
        assertFalse(all.get(0).active(), "a warning is stored inactive");
        assertFalse(all.get(4).active(), "a kick is stored inactive");
        assertTrue(all.get(2).active(), "a mute is active until it is lifted");
        assertTrue(all.get(3).active(), "a ban is active until it is lifted");
    }

    private static List<Long> ids(List<HistoryEntry> rows) {
        return rows.stream().map(HistoryEntry::id).toList();
    }
}
