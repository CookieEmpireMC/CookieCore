package dev.casperrs.cookieCore.chat;

import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import dev.casperrs.cookieCore.text.Messages;
import dev.casperrs.cookieCore.text.Template;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.Map;
import java.util.function.Supplier;

/** The two-line text in the server list. The ping event is async, so no Bukkit API is called here. */
public final class MotdListener implements Listener {

    private final ChatConfig config;
    private final Messages messages;
    private final Supplier<String> serverName;

    public MotdListener(ChatConfig config, Messages messages, Supplier<String> serverName) {
        this.config = config;
        this.messages = messages;
        this.serverName = serverName;
    }

    @EventHandler
    public void onPing(PaperServerListPingEvent event) {
        ChatSettings settings = config.current();
        if (settings == null || settings.motd() == null) {
            return;
        }
        Map<String, String> values = Map.of("server", serverName.get(),
                "online", event.getNumPlayers() < 0 ? messages.raw("chat.motd-hidden-count") : String.valueOf(event.getNumPlayers()),
                "max", String.valueOf(event.getMaxPlayers()));
        event.motd(render(settings.motd(), values));
    }

    static Component render(ChatSettings.Motd motd, Map<String, String> values) {
        Template second = motd.second();
        Component first = motd.first().render(values, Map.of());
        return second == null ? first : Component.join(JoinConfiguration.newlines(), first, second.render(values, Map.of()));
    }
}
