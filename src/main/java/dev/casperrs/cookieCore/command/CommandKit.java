package dev.casperrs.cookieCore.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.casperrs.cookieCore.api.PlayerRef;
import dev.casperrs.cookieCore.player.PlayerDirectory;
import dev.casperrs.cookieCore.sound.SoundEvents;
import dev.casperrs.cookieCore.sound.SoundService;
import dev.casperrs.cookieCore.text.Messages;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.List;
import java.util.function.Predicate;

/**
 * Building blocks for Brigadier commands, so a new command is a short tree of lines like:
 * <pre>{@code
 * kit.literal("mute", Perm.MODERATION_MUTE)
 *     .then(kit.knownPlayer("target")
 *         .then(kit.duration("time")
 *             .then(kit.reason("reason").executes(kit.checked(Perm.MODERATION_MUTE, ctx -> {
 *                 PlayerRef target = CommandKit.player(ctx, "target");
 *                 Duration time = CommandKit.duration(ctx, "time");
 *                 return kit.done(ctx, "moderation.muted", Placeholder.unparsed("player", target.name()));
 *             })))))
 * }</pre>
 *
 * <p>Conventions (same as CookieClicker's {@code BakeryCommand}): one final class per command tree with a
 * {@code node()} method, a permission on the root and on each sub-literal, handlers return
 * {@link Command#SINGLE_SUCCESS} or {@code 0} after telling the sender why, and every word shown to a player comes from
 * {@link Messages}. Arguments: {@link #knownPlayer} (online or seen before, from the memory cache),
 * {@link #onlinePlayer}, {@link #duration} ({@code 7d12h}) and {@link #reason} (greedy, must be last).
 */
public final class CommandKit {

    private final Messages messages;
    private final SoundService sounds;
    private final KnownPlayerArgument knownPlayer;
    private final DurationArgument duration;

    public CommandKit(Messages messages, SoundService sounds, PlayerDirectory directory) {
        this.messages = messages;
        this.sounds = sounds;
        this.knownPlayer = new KnownPlayerArgument(directory, messages);
        this.duration = new DurationArgument(messages);
    }

    // --- tree building -------------------------------------------------------------------------------------------

    /** A literal that only senders with {@code permission} can see and run (the check is on the sender, not the executor). */
    public LiteralArgumentBuilder<CommandSourceStack> literal(String name, String permission) {
        return Commands.literal(name).requires(permitted(permission));
    }

    /** True when the sender has at least one of the permissions. Use as a {@code requires(...)} for a root with several areas. */
    public static Predicate<CommandSourceStack> permitted(String... permissions) {
        List<String> nodes = List.of(permissions);
        return src -> nodes.stream().anyMatch(src.getSender()::hasPermission);
    }

    /**
     * Wraps a handler so the permission is checked again when it runs (belt and braces next to {@code requires}); a
     * sender who lost it gets {@code command.no-permission}.
     */
    public Command<CommandSourceStack> checked(String permission, Command<CommandSourceStack> body) {
        return ctx -> {
            if (!ctx.getSource().getSender().hasPermission(permission)) {
                return fail(ctx, "command.no-permission");
            }
            return body.run(ctx);
        };
    }

    // --- arguments -----------------------------------------------------------------------------------------------

    /** A player Core has seen before, online or not. Read with {@link #player}. */
    public RequiredArgumentBuilder<CommandSourceStack, PlayerRef> knownPlayer(String name) {
        return Commands.argument(name, knownPlayer);
    }

    /** One online player (vanilla selector, e.g. a name or {@code @p}). Read with {@link #onlinePlayer}. */
    public RequiredArgumentBuilder<CommandSourceStack, PlayerSelectorArgumentResolver> onlinePlayer(String name) {
        return Commands.argument(name, ArgumentTypes.player());
    }

    /** A real-time duration like {@code 7d12h}. Read with {@link #duration(CommandContext, String)}. */
    public RequiredArgumentBuilder<CommandSourceStack, Duration> duration(String name) {
        return Commands.argument(name, duration);
    }

    /** The rest of the line as free text. Must be the last argument. Read with {@link #reason}. */
    public RequiredArgumentBuilder<CommandSourceStack, String> reason(String name) {
        return Commands.argument(name, StringArgumentType.greedyString());
    }

    public static PlayerRef player(CommandContext<CommandSourceStack> ctx, String name) {
        return ctx.getArgument(name, PlayerRef.class);
    }

    public static Player onlinePlayer(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
        return ctx.getArgument(name, PlayerSelectorArgumentResolver.class).resolve(ctx.getSource()).getFirst();
    }

    public static Duration duration(CommandContext<CommandSourceStack> ctx, String name) {
        return ctx.getArgument(name, Duration.class);
    }

    public static String reason(CommandContext<CommandSourceStack> ctx, String name) {
        return StringArgumentType.getString(ctx, name);
    }

    // --- replies -------------------------------------------------------------------------------------------------

    /** The message {@code key} plus the error sound; returns {@code 0}. */
    public int fail(CommandContext<CommandSourceStack> ctx, String key, TagResolver... placeholders) {
        CommandSender sender = ctx.getSource().getSender();
        messages.send(sender, key, placeholders);
        sounds.play(sender, SoundEvents.UI_ERROR);
        return 0;
    }

    /** The message {@code key} plus the confirm sound; returns {@link Command#SINGLE_SUCCESS}. */
    public int done(CommandContext<CommandSourceStack> ctx, String key, TagResolver... placeholders) {
        CommandSender sender = ctx.getSource().getSender();
        messages.send(sender, key, placeholders);
        sounds.play(sender, SoundEvents.UI_CONFIRM);
        return Command.SINGLE_SUCCESS;
    }

    /** The message {@code key} without a sound; returns {@link Command#SINGLE_SUCCESS}. */
    public int say(CommandContext<CommandSourceStack> ctx, String key, TagResolver... placeholders) {
        messages.send(ctx.getSource().getSender(), key, placeholders);
        return Command.SINGLE_SUCCESS;
    }
}
