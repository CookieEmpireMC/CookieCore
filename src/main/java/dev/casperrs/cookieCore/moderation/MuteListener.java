package dev.casperrs.cookieCore.moderation;

import dev.casperrs.cookieCore.log.WarnOnce;
import dev.casperrs.cookieCore.storage.MuteRecord;
import dev.casperrs.cookieCore.text.Messages;
import dev.casperrs.cookieCore.text.TimeText;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/** Cancels the chat and the whisper commands of muted players. Memory only, no database, no thread hop. */
public final class MuteListener implements Listener {

    private static final Set<String> WHISPER_LABELS = Set.of("msg", "tell", "w", "whisper", "pm", "r", "reply", "me");

    private final MuteList mutes;
    private final Messages messages;
    private final LongSupplier clock;
    private final WarnOnce warnOnce;
    private volatile Predicate<Player> chatExemption = player -> false;

    public MuteListener(MuteList mutes, Messages messages, LongSupplier clock, WarnOnce warnOnce) {
        this.mutes = mutes;
        this.messages = messages;
        this.clock = clock;
        this.warnOnce = warnOnce;
    }

    /** Muted players for whom {@code exempt} is true are not stopped in public chat; asked on the chat thread. */
    public void exemptFromChatMute(Predicate<Player> exempt) {
        chatExemption = exempt;
    }

    /**
     * The vanilla whisper commands would let a muted player talk around the mute. {@code PlayerCommandPreprocessEvent} is
     * used (not deprecated in 26.3) because it carries the raw line, so the {@code minecraft:} prefix and the alias are
     * read in one place; a Brigadier-only hook would need one node per alias. Main thread, memory only.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!mutes.loaded() || !isWhisper(event.getMessage())) {
            return;
        }
        if (mutes.active(event.getPlayer().getUniqueId(), clock.getAsLong()).isPresent()) {
            event.setCancelled(true);
            messages.send(event.getPlayer(), "moderation.muted-command");
        }
    }

    static boolean isWhisper(String line) {
        int end = line.indexOf(' ');
        String label = (end < 0 ? line : line.substring(0, end)).toLowerCase(Locale.ROOT);
        if (!label.startsWith("/")) {
            return false;
        }
        label = label.substring(1);
        if (label.startsWith("minecraft:")) {
            label = label.substring("minecraft:".length());
        }
        return WHISPER_LABELS.contains(label);
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
        if (mute.isEmpty() || chatExemption.test(event.getPlayer())) {
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
