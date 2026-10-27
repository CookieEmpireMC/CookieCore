package dev.casperrs.cookieCore.moderation;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.casperrs.cookieCore.api.PlayerRef;
import dev.casperrs.cookieCore.command.KnownPlayerArgument;
import io.papermc.paper.command.brigadier.argument.CustomArgumentType;

import java.util.concurrent.CompletableFuture;
import java.util.function.LongSupplier;

/** Parses like {@link KnownPlayerArgument}, but completion offers only the players with an active ban. */
final class BannedPlayerArgument implements CustomArgumentType.Converted<PlayerRef, String> {

    private static final int MAX_SUGGESTIONS = 200;

    private final KnownPlayerArgument known;
    private final BanList bans;
    private final LongSupplier clock;

    BannedPlayerArgument(KnownPlayerArgument known, BanList bans, LongSupplier clock) {
        this.known = known;
        this.bans = bans;
        this.clock = clock;
    }

    @Override
    public PlayerRef convert(String nativeType) throws CommandSyntaxException {
        return known.convert(nativeType);
    }

    @Override
    public ArgumentType<String> getNativeType() {
        return known.getNativeType();
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        bans.activeNames(builder.getRemainingLowerCase(), MAX_SUGGESTIONS, clock.getAsLong()).forEach(builder::suggest);
        return builder.buildFuture();
    }
}
