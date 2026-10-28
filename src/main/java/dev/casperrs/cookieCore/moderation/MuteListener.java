package dev.casperrs.cookieCore.moderation;

import dev.casperrs.cookieCore.log.WarnOnce;
import dev.casperrs.cookieCore.storage.MuteRecord;
import dev.casperrs.cookieCore.text.Messages;
import dev.casperrs.cookieCore.text.TimeText;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.time.Duration;
import java.util.Optional;
import java.util.function.LongSupplier;

/** Cancels the chat of muted players. Runs on the async chat thread: memory only, no database, no thread hop. */
public final class MuteListener implements Listener {

    private final MuteList mutes;
    private final Messages messages;
    private final LongSupplier clock;
    private final WarnOnce warnOnce;

    public MuteListener(MuteList mutes, Messages messages, LongSupplier clock, WarnOnce warnOnce) {
        this.mutes = mutes;
        this.messages = messages;
        this.clock = clock;
        this.warnOnce = warnOnce;
    }

    /** LOWEST, so plugins that ignore cancelled events never format or relay the message. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        if (!mutes.loaded()) {
            warnOnce.warn("mutes-not-loaded", "The mutes are not loaded: chat is not blocked until they are");
            return;
        }
        long now = clock.getAsLong();
        Optional<MuteRecord> mute = mutes.active(event.getPlayer().getUniqueId(), now);
        if (mute.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        MuteRecord record = mute.get();
        if (record.expiresAt() == null) {
            messages.send(event.getPlayer(), "moderation.muted-chat-permanent", Placeholder.unparsed("reason", record.reason()));
        } else {
            messages.send(event.getPlayer(), "moderation.muted-chat", Placeholder.unparsed("reason", record.reason()),
                    Placeholder.unparsed("remaining", TimeText.remaining(Duration.ofMillis(record.expiresAt() - now))));
        }
    }
}
