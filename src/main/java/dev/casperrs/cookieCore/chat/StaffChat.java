package dev.casperrs.cookieCore.chat;

import dev.casperrs.cookieCore.text.Messages;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** The staff channel: who has it switched on, and delivery of a line to everyone who may read it. */
public final class StaffChat {

    private final Set<UUID> toggled = ConcurrentHashMap.newKeySet();
    private final ChatConfig config;
    private final Messages messages;
    private final RankSource ranks;
    private final Supplier<String> serverName;
    private final Supplier<? extends Collection<? extends Audience>> readers;

    /** {@code readers} are the online players with the staff chat permission plus the console; asked on the main thread. */
    public StaffChat(ChatConfig config, Messages messages, RankSource ranks, Supplier<String> serverName,
                     Supplier<? extends Collection<? extends Audience>> readers) {
        this.config = config;
        this.messages = messages;
        this.ranks = ranks;
        this.serverName = serverName;
        this.readers = readers;
    }

    /** Flips the toggle and returns whether the player's chat now goes to the channel. */
    public boolean toggle(UUID player) {
        if (toggled.remove(player)) {
            return false;
        }
        toggled.add(player);
        return true;
    }

    /** Safe from the chat thread. */
    public boolean active(UUID player) {
        return toggled.contains(player);
    }

    public void forget(UUID player) {
        toggled.remove(player);
    }

    /** A line typed as a command; the text is shown as typed. Main thread. */
    public void say(Player from, String text) {
        deliver(from, Map.of("message", text), Map.of());
    }

    /** A line from the player's chat while the toggle is on. Main thread. */
    public void say(Player from, Component message) {
        deliver(from, Map.of(), Map.of("message", message));
    }

    private void deliver(Player from, Map<String, String> text, Map<String, Component> components) {
        ChatSettings settings = config.current();
        if (settings == null) {
            return;
        }
        Map<String, String> allText = new HashMap<>(text);
        allText.put("server", serverName.get());
        Map<String, Component> allComponents = new HashMap<>(components);
        allComponents.put("rank_prefix", ranks.prefix(from).orElse(Component.empty()));
        allComponents.put("player", from.displayName());
        Component line = settings.staffFormat().render(allText, allComponents);
        readers.get().forEach(reader -> messages.deliver(reader, line));
    }
}
