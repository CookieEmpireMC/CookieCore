package dev.casperrs.cookieCore.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.casperrs.cookieCore.Perm;
import dev.casperrs.cookieCore.TestSupport;
import dev.casperrs.cookieCore.chat.ChatFixture.FakePlayer;
import dev.casperrs.cookieCore.log.WarnOnce;
import dev.casperrs.cookieCore.moderation.MuteList;
import dev.casperrs.cookieCore.moderation.MuteListener;
import dev.casperrs.cookieCore.storage.MuteRecord;
import dev.casperrs.cookieCore.text.Messages;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.chat.SignedMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

/**
 * The muted-chat rule across MuteListener (LOWEST) and ChatListener (HIGH, ignoreCancelled), with the staff exemption as
 * ChatModule builds it: the toggle and the staff permission together. ChatModule itself needs a JavaPlugin, so the same
 * expression is written here.
 */
class ChatListenerTest {

    private static final long NOW = 1_000_000_000_000L;

    private final TestSupport.LogCapture log = new TestSupport.LogCapture();
    private final Messages messages = ChatFixture.shippedMessages();
    private final MuteList mutes = new MuteList();
    private final List<FakePlayer> online = new ArrayList<>();
    private final StaffChat staff;
    private final ChatListener chat;
    private final MuteListener mute;

    ChatListenerTest() {
        mutes.markLoaded();
        ChatConfig config = ChatFixture.shippedConfig();
        config.load(new ArrayList<>());
        RankSource noRanks = player -> Optional.empty();
        staff = new StaffChat(config, messages, noRanks, () -> ChatFixture.SERVER,
                () -> online.stream().filter(p -> p.player.hasPermission(Perm.STAFF_CHAT)).map(p -> p.player).toList());
        chat = new ChatListener(config, noRanks, staff, Runnable::run, () -> ChatFixture.SERVER);
        mute = new MuteListener(mutes, messages, () -> NOW, new WarnOnce(log.logger));
        mute.exemptFromChatMute(p -> staff.active(p.getUniqueId()) && p.hasPermission(Perm.STAFF_CHAT));
    }

    private AsyncChatEvent chatEvent(FakePlayer p, String text) {
        SignedMessage signed = (SignedMessage) Proxy.newProxyInstance(SignedMessage.class.getClassLoader(),
                new Class<?>[] {SignedMessage.class}, (proxy, method, args) -> TestSupport.defaultValue(method.getReturnType()));
        return new AsyncChatEvent(true, p.player, new HashSet<Audience>(), ChatRenderer.defaultRenderer(),
                Component.text(text), Component.text(text), signed);
    }

    /** The order the server runs the two listeners in: LOWEST first, and HIGH only for a line that was not cancelled. */
    private void dispatch(AsyncChatEvent event) {
        mute.onChat(event);
        if (!event.isCancelled()) {
            chat.onChat(event);
        }
    }

    private void mute(FakePlayer p, Boolean permanent) {
        mutes.put(new MuteRecord(1L, p.id, p.name, "Boss", "spam", NOW, permanent ? null : NOW + 60_000L));
    }

    /** A muted player who lost the staff permission while their toggle is on is stopped, not sent to public chat. */
    @Test
    void aMutedStaffMemberWhoLostThePermissionStaysMutedInPublicChat() {
        FakePlayer lost = new FakePlayer("Lost");
        online.add(lost);
        staff.toggle(lost.id);
        mute(lost, true);
        AsyncChatEvent event = chatEvent(lost, "hi");
        dispatch(event);
        assertTrue(event.isCancelled(), "the mute cancels the line");
        assertEquals(1, lost.rec.messages.size(), "the muted player is told why, and nothing else is sent");
        assertTrue(staff.active(lost.id), "the toggle is still on, so only the permission stops the exemption");
    }

    /** A muted staff member with the permission talks in the staff channel only, and the mute does not stop that. */
    @Test
    void aMutedStaffMemberWithThePermissionTalksOnlyInTheStaffChannel() {
        FakePlayer staffer = new FakePlayer("Staffer", Perm.STAFF_CHAT);
        FakePlayer alex = new FakePlayer("Alex");
        online.add(staffer);
        online.add(alex);
        staff.toggle(staffer.id);
        mute(staffer, true);
        AsyncChatEvent event = chatEvent(staffer, "hi");
        dispatch(event);
        assertTrue(event.isCancelled(), "the public line is cancelled and delivered as a staff line");
        assertEquals(List.of("[Staff] Staffer » hi"), staffer.rec.plain());
        assertTrue(alex.rec.messages.isEmpty(), "a player without the permission never sees the staff channel");
    }

    /** An unmuted player without the permission and with the toggle off gets the public format. */
    @Test
    void anUnmutedPlayerWithoutThePermissionGetsThePublicFormat() {
        FakePlayer steve = new FakePlayer("Steve");
        online.add(steve);
        AsyncChatEvent event = chatEvent(steve, "hi");
        dispatch(event);
        assertFalse(event.isCancelled());
        assertEquals("Steve » hi", PlainTextComponentSerializer.plainText().serialize(
                event.renderer().render(steve.player, Component.text("Steve"), Component.text("hi"), steve.player)));
    }

    /** The staff readers are the online players with the permission, then the console; nobody else. */
    @Test
    void staffReadersArePermittedPlayersAndTheConsole() {
        FakePlayer staffer = new FakePlayer("Staffer", Perm.STAFF_CHAT);
        FakePlayer alex = new FakePlayer("Alex");
        Audience console = TestSupport.console(new TestSupport.Recorder());
        List<Audience> readers = ChatModule.staffReaders(List.of(staffer.player, alex.player), console);
        assertEquals(2, readers.size(), readers.toString());
        assertSame(staffer.player, readers.get(0));
        assertSame(console, readers.get(1));
    }
}
