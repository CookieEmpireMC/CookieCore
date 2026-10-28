package dev.casperrs.cookieCore.moderation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.casperrs.cookieCore.log.WarnOnce;
import dev.casperrs.cookieCore.moderation.ModerationFixture.TestPlayer;
import dev.casperrs.cookieCore.storage.MuteRecord;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.HashSet;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MuteListenerTest {

    private ModerationFixture f;
    private MuteListener listener;

    @BeforeEach
    void setUp() throws Exception {
        f = new ModerationFixture();
        listener = new MuteListener(f.mutes, f.messages, () -> f.now, new WarnOnce(f.log.logger));
    }

    /** A muted player's chat is cancelled and says why; everyone else's chat goes through. */
    @Test
    void theMutedPlayerIsCancelledWithTheReasonAndOthersPass() {
        TestPlayer steve = f.player("Steve");
        TestPlayer alex = f.player("Alex");
        f.mutes.markLoaded();
        f.mutes.put(new MuteRecord(1, steve.id, steve.name, "Alex", "spam", f.now, null));

        AsyncChatEvent muted = chat(steve);
        listener.onChat(muted);
        assertTrue(muted.isCancelled());
        assertTrue(steve.rec.lastPlain().contains("spam"), steve.rec.lastPlain());

        AsyncChatEvent other = chat(alex);
        listener.onChat(other);
        assertFalse(other.isCancelled());
    }

    /** Until the start-up load ran, chat is not blocked (and the player is not told anything). */
    @Test
    void chatIsNotBlockedWhileTheMutesAreNotLoaded() {
        TestPlayer steve = f.player("Steve");
        f.mutes.put(new MuteRecord(1, steve.id, steve.name, "Alex", "spam", f.now, null));

        AsyncChatEvent event = chat(steve);
        listener.onChat(event);
        assertFalse(event.isCancelled());
    }

    /** An expired tempmute still in the map does not block chat. */
    @Test
    void anExpiredTempmuteLetsTheChatThrough() {
        TestPlayer steve = f.player("Steve");
        f.mutes.markLoaded();
        f.mutes.put(new MuteRecord(1, steve.id, steve.name, "Alex", "spam", f.now - 10_000, f.now));

        AsyncChatEvent event = chat(steve);
        listener.onChat(event);
        assertFalse(event.isCancelled());
    }

    private static AsyncChatEvent chat(TestPlayer player) {
        return new AsyncChatEvent(true, player.player, new HashSet<Audience>(), (source, name, message, viewer) -> message,
                Component.text("hello"), Component.text("hello"), null);
    }
}
