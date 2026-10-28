package dev.casperrs.cookieCore.chat;

import dev.casperrs.cookieCore.TestSupport;
import dev.casperrs.cookieCore.text.Messages;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

/** Shared setup for the chat tests: the shipped chat.yml and messages.yml, and a Player proxy that records what it gets. */
final class ChatFixture {

    static final String SERVER = "Cookie Empire";

    private ChatFixture() {
    }

    /** The shipped chat.yml, as both the admin's file and the jar's copy. */
    static ChatConfig shippedConfig() {
        return new ChatConfig(() -> Optional.of(TestSupport.resourceYaml("chat.yml")),
                () -> Optional.of(TestSupport.resourceYaml("chat.yml")));
    }

    static Messages shippedMessages() {
        Messages messages = new Messages(new TestSupport.LogCapture().logger);
        messages.load(TestSupport.resourceYaml("messages.yml"));
        return messages;
    }

    /** A player with a name, a UUID and the given permissions; every message, title and sound sent to it is kept. */
    static final class FakePlayer {

        final String name;
        final UUID id = UUID.randomUUID();
        final Player player;
        final TestSupport.Recorder rec = new TestSupport.Recorder();
        final List<Title> titles = new ArrayList<>();
        boolean playedBefore = true;

        FakePlayer(String name, String... permissions) {
            this.name = name;
            Set<String> granted = Set.of(permissions);
            this.player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "hasPermission" -> granted.contains((String) args[0]);
                        case "hasPlayedBefore" -> playedBefore;
                        case "getName" -> name;
                        case "getUniqueId" -> id;
                        case "displayName" -> Component.text(name);
                        case "sendMessage" -> {
                            if (args[0] instanceof Component c) {
                                rec.messages.add(c);
                            }
                            yield null;
                        }
                        case "showTitle" -> {
                            titles.add((Title) args[0]);
                            yield null;
                        }
                        case "playSound" -> {
                            if (args[0] instanceof Sound s) {
                                rec.sounds.add(s);
                            }
                            yield null;
                        }
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        case "toString" -> "FakePlayer(" + name + ")";
                        default -> TestSupport.defaultValue(method.getReturnType());
                    });
        }
    }
}
