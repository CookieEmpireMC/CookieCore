package dev.casperrs.cookieCore.chat;

import dev.casperrs.cookieCore.Perm;
import dev.casperrs.cookieCore.rank.PermissionRankSource;
import dev.casperrs.cookieCore.text.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.function.Supplier;

/** Join and quit lines, the welcome, and the per-player chat state that starts at join and ends at quit. */
public final class JoinListener implements Listener {

    private final Messages messages;
    private final ChatConfig config;
    private final Supplier<String> serverName;
    private final PermissionRankSource ranks;
    private final ReplyTracker replies;
    private final StaffChat staff;
    private final MuteExpiryNotifier expiry;

    public JoinListener(Messages messages, ChatConfig config, Supplier<String> serverName, PermissionRankSource ranks,
                        ReplyTracker replies, StaffChat staff, MuteExpiryNotifier expiry) {
        this.messages = messages;
        this.config = config;
        this.serverName = serverName;
        this.ranks = ranks;
        this.replies = replies;
        this.staff = staff;
        this.expiry = expiry;
    }

    /** HIGH so our line wins over plugins at the default priority; the event cannot be cancelled. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        ranks.refresh(player);
        expiry.start();
        if (player.hasPermission(Perm.CHAT_SILENTJOIN)) {
            event.joinMessage(null);
        } else {
            event.joinMessage(messages.component(player.hasPlayedBefore() ? "chat.join" : "chat.join-first", name(player)));
        }
        welcome(player);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        event.quitMessage(player.hasPermission(Perm.CHAT_SILENTJOIN) ? null : messages.component("chat.quit", name(player)));
        ranks.forget(player.getUniqueId());
        replies.forget(player.getUniqueId());
        staff.forget(player.getUniqueId());
    }

    private void welcome(Player player) {
        ChatSettings settings = config.current();
        ChatSettings.Welcome times = settings != null ? settings.welcome()
                : new ChatSettings.Welcome(ChatSettings.DEFAULT_FADE_IN, ChatSettings.DEFAULT_STAY, ChatSettings.DEFAULT_FADE_OUT);
        TagResolver[] values = {name(player), Placeholder.unparsed("server", serverName.get())};
        messages.title(player, "chat.welcome-title", "chat.welcome-subtitle", times.fadeInTicks(), times.stayTicks(),
                times.fadeOutTicks(), values);
        if (!messages.isBlank("chat.welcome-line")) {
            messages.send(player, "chat.welcome-line", values);
        }
    }

    private static TagResolver name(Player player) {
        return Placeholder.unparsed("player", player.getName());
    }
}
