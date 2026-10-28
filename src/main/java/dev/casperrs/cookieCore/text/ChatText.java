package dev.casperrs.cookieCore.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/** Turns text that an admin wrote (chat.yml, a LuckPerms prefix) into components. Never for text typed by players. */
public final class ChatText {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder().character('&').hexColors().build();

    private ChatText() {
    }

    /** MiniMessage without placeholders, e.g. a rank prefix from chat.yml. */
    public static Component mini(String miniMessage) {
        return Messages.miniMessage().deserialize(miniMessage);
    }

    /** A legacy string with {@code &} colour codes and {@code &#rrggbb}, which is how LuckPerms stores prefixes. */
    public static Component legacy(String text) {
        return LEGACY.deserialize(text);
    }
}
