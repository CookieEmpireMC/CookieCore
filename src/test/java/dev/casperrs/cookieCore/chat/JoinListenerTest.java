package dev.casperrs.cookieCore.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.casperrs.cookieCore.Perm;
import dev.casperrs.cookieCore.TestSupport;
import dev.casperrs.cookieCore.chat.ChatFixture.FakePlayer;
import dev.casperrs.cookieCore.moderation.MuteList;
import dev.casperrs.cookieCore.text.Messages;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerQuitEvent.QuitReason;
import org.junit.jupiter.api.Test;

class JoinListenerTest {

    private final Messages messages = ChatFixture.shippedMessages();
    private final ReplyTracker replies = new ReplyTracker();
    private final StaffChat staff;
    private final JoinListener listener;

    JoinListenerTest() {
        ChatConfig config = ChatFixture.shippedConfig();
        config.load(new ArrayList<>());
        PermissionRankSource ranks = new PermissionRankSource(config::current);
        staff = new StaffChat(config, messages, ranks, () -> ChatFixture.SERVER, () -> List.of());
        MuteExpiryNotifier expiry = new MuteExpiryNotifier(new MuteList(), () -> 0L, () -> List.of(), p -> { },
                (task, period) -> () -> { });
        listener = new JoinListener(messages, config, () -> ChatFixture.SERVER, ranks, replies, staff, expiry);
    }

    private static String plain(Component c) {
        return c == null ? null : PlainTextComponentSerializer.plainText().serialize(c);
    }

    private PlayerJoinEvent join(FakePlayer p) {
        PlayerJoinEvent event = new PlayerJoinEvent(p.player, Component.empty());
        listener.onJoin(event);
        return event;
    }

    private PlayerQuitEvent quit(FakePlayer p) {
        PlayerQuitEvent event = new PlayerQuitEvent(p.player, Component.empty(), QuitReason.DISCONNECTED);
        listener.onQuit(event);
        return event;
    }

    @Test
    void joinAndQuitLinesNameThePlayer() {
        FakePlayer steve = new FakePlayer("Steve");
        assertEquals("[+] Steve joined the game", plain(join(steve).joinMessage()));
        assertEquals("[-] Steve left the game", plain(quit(steve).quitMessage()));
    }

    /** A player who has never joined gets the first-join line, the welcome title and the welcome line. */
    @Test
    void firstJoinGetsTheFirstLineAndTheWelcome() {
        FakePlayer newbie = new FakePlayer("Newbie");
        newbie.playedBefore = false;
        assertEquals("[+] Newbie joined for the first time Welcome!", plain(join(newbie).joinMessage()));
        assertEquals(1, newbie.titles.size());
        assertEquals("Cookie Empire", plain(newbie.titles.get(0).title()));
        assertEquals("Welcome, Newbie!", plain(newbie.titles.get(0).subtitle()));
        assertTrue(newbie.rec.lastPlain().endsWith("Welcome, Newbie! Have a cookie."), newbie.rec.plain().toString());
    }

    /** The silent-join permission removes both lines and nothing else. */
    @Test
    void theSilentJoinPermissionHidesJoinAndQuit() {
        FakePlayer ghost = new FakePlayer("Ghost", Perm.CHAT_SILENTJOIN);
        assertNull(join(ghost).joinMessage());
        assertNull(quit(ghost).quitMessage());
    }

    /** Blank welcome texts in messages.yml switch that part off (none shown, no empty title). */
    @Test
    void blankWelcomeTextsSwitchTheWelcomeOff() {
        YamlConfiguration yaml = TestSupport.resourceYaml("messages.yml");
        yaml.set("chat.welcome-title", "");
        yaml.set("chat.welcome-subtitle", "");
        yaml.set("chat.welcome-line", "");
        messages.load(yaml);
        FakePlayer newbie = new FakePlayer("Newbie");
        join(newbie);
        assertTrue(newbie.titles.isEmpty(), "no title when both are blank");
        assertTrue(newbie.rec.messages.isEmpty(), "no welcome line when blank");
    }

    /** Quitting drops the player's reply target and staff-channel toggle, so nothing carries over to the next session. */
    @Test
    void quitForgetsTheReplyTargetAndTheStaffToggle() {
        FakePlayer steve = new FakePlayer("Steve");
        FakePlayer alex = new FakePlayer("Alex");
        replies.record(steve.id, alex.id);
        staff.toggle(steve.id);
        quit(steve);
        assertNull(replies.partner(steve.id));
        assertFalse(staff.active(steve.id));
    }
}
