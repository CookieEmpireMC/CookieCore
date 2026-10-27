package dev.casperrs.cookieCore.moderation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.casperrs.cookieCore.storage.BanRecord;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BanListTest {

    private static final long NOW = 1_000_000L;
    private final UUID steve = UUID.randomUUID();

    private static BanRecord ban(long id, UUID who, Long expiresAt) {
        return new BanRecord(id, who, "Steve", "Alex", "spam", NOW - 1, expiresAt);
    }

    /** An expired tempban is ignored on read and left in the map; a new ban replaces whatever is stored. */
    @Test
    void expiredBansAreIgnoredAndANewBanReplacesTheStoredOne() {
        BanList bans = new BanList();
        bans.put(ban(1, steve, NOW - 1));
        assertTrue(bans.active(steve, NOW).isEmpty());
        assertTrue(bans.activeNames("", 10, NOW).isEmpty());

        bans.put(ban(2, steve, null));
        assertEquals(2, bans.active(steve, NOW).orElseThrow().id());
        bans.remove(steve);
        assertTrue(bans.active(steve, NOW).isEmpty());
    }

    /** Start-up with two active rows for one player keeps the one that lasts longer: permanent beats any tempban. */
    @Test
    void theLongerBanWinsWhenTheStartUpLoadFindsTwo() {
        BanList timed = new BanList();
        timed.load(List.of(ban(1, steve, NOW + 3_600_000L), ban(2, steve, NOW + 7_200_000L)));
        assertEquals(2, timed.active(steve, NOW).orElseThrow().id());

        BanList permanent = new BanList();
        permanent.load(List.of(ban(3, steve, NOW + 7_200_000L), ban(4, steve, null)));
        assertEquals(4, permanent.active(steve, NOW).orElseThrow().id());
    }
}
