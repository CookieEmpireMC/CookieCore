package dev.casperrs.cookieCore.gamemode;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.casperrs.cookieCore.text.Messages;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.MessageComponentSerializer;
import io.papermc.paper.command.brigadier.argument.CustomArgumentType;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.GameMode;

import java.util.concurrent.CompletableFuture;

/**
 * A game mode by name, number (0 to 3) or short name (s, c, a, sp). The client sees a plain word, because its own
 * game mode argument rejects numbers and short names. Only the full names are suggested, and only the modes the
 * sender may use.
 */
public final class GameModeArgument implements CustomArgumentType.Converted<GameMode, String> {

    private final DynamicCommandExceptionType invalid;

    public GameModeArgument(Messages messages) {
        this.invalid = new DynamicCommandExceptionType(input -> MessageComponentSerializer.message().serialize(
                messages.component("gamemode.invalid-mode", Placeholder.unparsed("input", String.valueOf(input)))));
    }

    @Override
    public GameMode convert(String nativeType) throws CommandSyntaxException {
        return GameModes.parse(nativeType).orElseThrow(() -> invalid.create(nativeType));
    }

    @Override
    public ArgumentType<String> getNativeType() {
        return StringArgumentType.word();
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        Object source = context.getSource();
        for (GameMode mode : GameModes.ALL) {
            String name = GameModes.name(mode);
            boolean allowed = !(source instanceof CommandSourceStack stack)
                    || stack.getSender().hasPermission(GameModes.permission(mode));
            if (allowed && name.startsWith(builder.getRemainingLowerCase())) {
                builder.suggest(name);
            }
        }
        return builder.buildFuture();
    }
}
