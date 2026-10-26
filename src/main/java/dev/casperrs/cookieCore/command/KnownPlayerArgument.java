package dev.casperrs.cookieCore.command;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.casperrs.cookieCore.api.PlayerRef;
import dev.casperrs.cookieCore.player.PlayerDirectory;
import dev.casperrs.cookieCore.text.Messages;
import io.papermc.paper.command.brigadier.MessageComponentSerializer;
import io.papermc.paper.command.brigadier.argument.CustomArgumentType;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * A player Core has seen join before, online or not. Backed by the {@link PlayerDirectory} memory cache, so it never
 * touches the database or the Mojang API (unlike {@code ArgumentTypes.playerProfiles()}). A name the cache does not
 * know fails with {@code command.unknown-player}; a command that must also find players first seen by another server
 * can call {@code PlayerDirectory.lookup} itself. The client sees a plain word.
 */
public final class KnownPlayerArgument implements CustomArgumentType.Converted<PlayerRef, String> {

    private static final int MAX_SUGGESTIONS = 200;

    private final PlayerDirectory directory;
    private final DynamicCommandExceptionType unknown;

    public KnownPlayerArgument(PlayerDirectory directory, Messages messages) {
        this.directory = directory;
        this.unknown = new DynamicCommandExceptionType(name -> MessageComponentSerializer.message().serialize(
                messages.component("command.unknown-player", Placeholder.unparsed("name", String.valueOf(name)))));
    }

    @Override
    public PlayerRef convert(String nativeType) throws CommandSyntaxException {
        Optional<PlayerRef> found = directory.cached(nativeType);
        if (found.isEmpty()) {
            throw unknown.create(nativeType);
        }
        return found.get();
    }

    @Override
    public ArgumentType<String> getNativeType() {
        return StringArgumentType.word();
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        directory.namesStartingWith(builder.getRemainingLowerCase(), MAX_SUGGESTIONS).forEach(builder::suggest);
        return builder.buildFuture();
    }
}
