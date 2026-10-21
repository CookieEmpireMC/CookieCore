package dev.casperrs.cookieCore.command;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.casperrs.cookieCore.text.Messages;
import io.papermc.paper.command.brigadier.MessageComponentSerializer;
import io.papermc.paper.command.brigadier.argument.CustomArgumentType;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** A real-time duration like {@code 7d12h} (see {@link DurationParser}). The client sees a plain word. */
public final class DurationArgument implements CustomArgumentType.Converted<Duration, String> {

    private static final List<String> SUGGESTIONS = List.of("30m", "1h", "12h", "1d", "7d", "30d");

    private final DynamicCommandExceptionType invalid;

    public DurationArgument(Messages messages) {
        this.invalid = new DynamicCommandExceptionType(input -> MessageComponentSerializer.message().serialize(
                messages.component("command.invalid-duration", Placeholder.unparsed("input", String.valueOf(input)))));
    }

    @Override
    public Duration convert(String nativeType) throws CommandSyntaxException {
        var parsed = DurationParser.parse(nativeType);
        if (parsed.isEmpty()) {
            throw invalid.create(nativeType);
        }
        return parsed.get();
    }

    @Override
    public ArgumentType<String> getNativeType() {
        return StringArgumentType.word();
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        String typed = builder.getRemainingLowerCase();
        for (String suggestion : SUGGESTIONS) {
            if (suggestion.startsWith(typed)) {
                builder.suggest(suggestion);
            }
        }
        return builder.buildFuture();
    }
}
