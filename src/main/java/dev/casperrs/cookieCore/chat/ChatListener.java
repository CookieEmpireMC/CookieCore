package dev.casperrs.cookieCore.chat;

import dev.casperrs.cookieCore.Perm;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;

/**
 * Formats public chat with {@code chat.format}, or moves it to the staff channel while the player has that switched on.
 * Runs on the chat thread: it reads only immutable settings and thread-safe caches.
 */
public final class ChatListener implements Listener {

    private final ChatConfig config;
    private final RankSource ranks;
    private final StaffChat staff;
    private final Executor main;
    private final Supplier<String> serverName;

    /** {@code main} runs a task on the main thread. */
    public ChatListener(ChatConfig config, RankSource ranks, StaffChat staff, Executor main, Supplier<String> serverName) {
        this.config = config;
        this.ranks = ranks;
        this.staff = staff;
        this.main = main;
        this.serverName = serverName;
    }

    /**
     * HIGH so a plugin at the default priority cannot overwrite the renderer, and {@code ignoreCancelled} so a muted player
     * (cancelled by MuteListener at LOWEST) never reaches it. Only the renderer is set, never {@code message(...)}: the signed
     * message stays valid, and the text goes in as a component, so {@code <red>} typed by a player stays literal.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player sender = event.getPlayer();
        if (staff.active(sender.getUniqueId()) && sender.hasPermission(Perm.STAFF_CHAT)) {
            event.setCancelled(true);
            Component message = event.message();
            try {
                main.execute(() -> staff.say(sender, message));
            } catch (RejectedExecutionException shutDown) {
                // the server is stopping; the line is dropped
            }
            return;
        }
        ChatSettings settings = config.current();
        if (settings == null) {
            return;
        }
        String server = serverName.get();
        event.renderer(ChatRenderer.viewerUnaware((source, displayName, message) -> settings.format().render(
                Map.of("server", server),
                Map.of("rank_prefix", ranks.prefix(source).orElse(Component.empty()), "player", displayName, "message", message))));
    }
}
