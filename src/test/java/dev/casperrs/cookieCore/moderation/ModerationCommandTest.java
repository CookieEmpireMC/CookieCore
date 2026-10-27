package dev.casperrs.cookieCore.moderation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.casperrs.cookieCore.Perm;
import dev.casperrs.cookieCore.TestSupport;
import dev.casperrs.cookieCore.command.KnownPlayerArgument;
import dev.casperrs.cookieCore.moderation.ModerationFixture.TestPlayer;
import dev.casperrs.cookieCore.storage.BanRecord;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The command trees through a plain Brigadier dispatcher, with the real services behind them. */
class ModerationCommandTest {

    private ModerationFixture f;

    @BeforeEach
    void setUp() throws Exception {
        f = new ModerationFixture();
        f.dispatcher.getRoot().addChild(new KickCommand(f.kit, f.service).node());
        f.dispatcher.getRoot().addChild(new BanCommand(f.kit, f.service).node());
        f.dispatcher.getRoot().addChild(new TempbanCommand(f.kit, f.service).node());
        f.dispatcher.getRoot().addChild(new UnbanCommand(f.kit, f.service,
                new BannedPlayerArgument(new KnownPlayerArgument(f.directory, f.messages), f.bans, () -> f.now)).node());
    }

    /** Each command is hidden and refused for a sender without its own node, even when the sender has the others. */
    @Test
    void eachCommandIsHiddenFromASenderWithoutItsNode() {
        TestPlayer none = f.player("Nobody");
        TestPlayer staff = f.player("Staffer", Perm.STAFF_KICK, Perm.STAFF_BAN);
        CommandSourceStack asStaff = TestSupport.source(staff.player);
        CommandSourceStack asNobody = TestSupport.source(none.player);

        assertFalse(f.dispatcher.getRoot().getChild("unban").canUse(asStaff), "unban needs its own node");
        assertTrue(f.dispatcher.getRoot().getChild("tempban").canUse(asStaff));
        assertFalse(f.dispatcher.getRoot().getChild("kick").canUse(asNobody));
        assertFalse(f.dispatcher.getRoot().getChild("ban").canUse(asNobody));
        assertThrows(CommandSyntaxException.class, () -> f.run(none.player, "ban Staffer"), "unknown to a sender without it");
    }

    /** The flag wins over a reason; {@code -sx} and {@code -S} are reasons; a reason may end with a bare {@code -s}. */
    @Test
    void theSilentFlagAndTheReasonEndingsParseAsDocumented() throws Exception {
        List<String> seen = new ArrayList<>();
        f.dispatcher.getRoot().addChild(Commands.literal("x").then(SilentReasonTail.attach(
                Commands.argument("t", StringArgumentType.word()), f.kit, "x.test",
                (ctx, silent, reason) -> {
                    seen.add(silent + " " + reason);
                    return 1;
                })).build());
        CommandSender console = TestSupport.console(new TestSupport.Recorder(), "x.test");
        String[][] cases = {
            {"x Steve", "false null"},
            {"x Steve spam", "false spam"},
            {"x Steve -s", "true null"},
            {"x Steve -s spam", "true spam"},
            {"x Steve -sx", "false -sx"},
            {"x Steve -S", "false -S"},
            {"x Steve spam -s", "false spam -s"},
            {"x Steve -s -s", "true -s"},
        };
        for (String[] c : cases) {
            seen.clear();
            f.run(console, c[0]);
            assertEquals(c[1], seen.getFirst(), c[0]);
        }
    }

    /** {@code /unban} completion offers only names with an active ban; an expired tempban is not among them. */
    @Test
    void unbanCompletionListsOnlyCurrentlyBannedNames() throws Exception {
        f.bans.put(new BanRecord(1, UUID.randomUUID(), "Alice", "Alex", "r", f.now, null));
        f.bans.put(new BanRecord(2, UUID.randomUUID(), "Carol", "Alex", "r", f.now, f.now + 60_000));
        f.bans.put(new BanRecord(3, UUID.randomUUID(), "Dave", "Alex", "r", f.now, f.now - 1));
        BannedPlayerArgument banned = new BannedPlayerArgument(new KnownPlayerArgument(f.directory, f.messages), f.bans, () -> f.now);

        List<String> names = banned.listSuggestions(null, new SuggestionsBuilder("", 0)).join().getList()
                .stream().map(Suggestion::getText).toList();
        assertEquals(List.of("Alice", "Carol"), names);
    }

    /** A tempban is stored with its end, a second one is refused with the end date, and self or an impossible length fail. */
    @Test
    void tempbanStoresTheEndAndRefusesASecondBanAndSelfBan() throws Exception {
        TestPlayer alex = f.player("Alex", Perm.STAFF_BAN);
        TestPlayer steve = f.player("Steve");

        assertEquals(1, f.run(alex.player, "tempban Steve 7d12h -s spam"));
        BanRecord ban = f.bans.active(steve.id, f.now).orElseThrow();
        assertEquals(f.now + Duration.ofDays(7).plusHours(12).toMillis(), ban.expiresAt());
        assertEquals(1, f.count("core_punishments"));
        assertEquals(0, f.run(alex.player, "tempban Steve 1d"));
        assertTrue(alex.rec.lastPlain().contains("is already banned until"), alex.rec.lastPlain());
        assertEquals(0, f.run(alex.player, "ban Alex"));
        assertTrue(alex.rec.lastPlain().contains("cannot do that to yourself"), alex.rec.lastPlain());
        assertThrows(CommandSyntaxException.class, () -> f.run(alex.player, "tempban Steve 0s"), "zero is not a length");
        assertThrows(CommandSyntaxException.class, () -> f.run(alex.player, "tempban Steve 37000d"), "over 100 years");
    }

    /** Kick, ban, unban from the commands; the unban marks the row inactive and names who removed it. */
    @Test
    void kickBanAndUnbanRunFromTheCommandsAndTheUnbanIsRecorded() throws Exception {
        TestPlayer alex = f.player("Alex", Perm.STAFF_KICK, Perm.STAFF_BAN, Perm.STAFF_UNBAN);
        TestPlayer bob = f.player("Bob");
        TestPlayer steve = f.player("Steve");

        assertEquals(1, f.run(alex.player, "kick Bob -s spam"));
        assertEquals(1, bob.kicks.size());
        assertEquals(1, f.run(alex.player, "ban Steve griefing the spawn"));
        assertEquals(1, steve.kicks.size(), "the banned target is removed at once");
        assertNull(f.bans.active(steve.id, f.now).orElseThrow().expiresAt());

        assertEquals(1, f.run(alex.player, "unban Steve fixed"));
        assertTrue(f.bans.active(steve.id, f.now).isEmpty());
        try (var c = f.raw(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT active, removed_by_name FROM core_punishments WHERE type = 'BAN'")) {
            assertTrue(rs.next());
            assertFalse(rs.getBoolean(1), "inactive after the unban");
            assertEquals("Alex", rs.getString(2));
        }
        assertEquals(0, f.run(alex.player, "unban Steve"));
        assertTrue(alex.rec.lastPlain().contains("is not banned"), alex.rec.lastPlain());
    }
}
