package dev.casperrs.cookieCore;

import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.Message;
import io.papermc.paper.command.brigadier.MessageComponentSerializer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.jspecify.annotations.NonNull;

/**
 * Stand-in for the server's {@code MessageComponentSerializer}, registered through
 * {@code META-INF/services} in the test resources (the real provider ships inside Paper). Plain text only.
 */
public final class PlainMessageSerializer implements MessageComponentSerializer {

    @Override
    public @NonNull Component deserialize(@NonNull Message input) {
        return Component.text(input.getString());
    }

    @Override
    public @NonNull Message serialize(@NonNull Component component) {
        return new LiteralMessage(PlainTextComponentSerializer.plainText().serialize(component));
    }
}
