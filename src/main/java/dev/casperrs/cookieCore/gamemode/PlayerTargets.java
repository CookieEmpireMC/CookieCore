package dev.casperrs.cookieCore.gamemode;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.casperrs.cookieCore.command.CommandKit;
import dev.casperrs.cookieCore.sound.SoundEvents;
import dev.casperrs.cookieCore.sound.SoundService;
import dev.casperrs.cookieCore.text.Messages;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Who a {@code gamemode}, {@code fly} or {@code speed} command acts on, and the "tell the target" part of it. Errors
 * are sent to the sender here ({@code <area>.console-needs-target}, {@code .no-target}, {@code .no-permission-others}),
 * so an empty result means the sender has already been told why.
 */
final class PlayerTargets {

    private final CommandKit kit;
    private final Messages messages;
    private final SoundService sounds;
    private final String area;
    private final String othersPermission;

    PlayerTargets(CommandKit kit, Messages messages, SoundService sounds, String area, String othersPermission) {
        this.kit = kit;
        this.messages = messages;
        this.sounds = sounds;
        this.area = area;
        this.othersPermission = othersPermission;
    }

    /** The executor ({@code /execute as} changes it; plain commands have the sender), which must be a player. */
    Optional<Player> self(CommandContext<CommandSourceStack> ctx) {
        if (ctx.getSource().getExecutor() instanceof Player player) {
            return allowed(ctx, player);
        }
        kit.fail(ctx, area + ".console-needs-target");
        return Optional.empty();
    }

    Optional<Player> named(CommandContext<CommandSourceStack> ctx, String argument) {
        List<Player> found;
        try {
            found = ctx.getArgument(argument, PlayerSelectorArgumentResolver.class).resolve(ctx.getSource());
        } catch (CommandSyntaxException e) {
            found = List.of();
        }
        if (found.isEmpty()) {
            kit.fail(ctx, area + ".no-target");
            return Optional.empty();
        }
        return allowed(ctx, found.getFirst());
    }

    static boolean isSelf(CommandContext<CommandSourceStack> ctx, Player target) {
        return ctx.getSource().getSender() instanceof Player sender && sender.getUniqueId().equals(target.getUniqueId());
    }

    static TagResolver player(Player target) {
        return Placeholder.unparsed("player", target.getName());
    }

    /** Tells the target who changed what ({@code <staff>} is added to the placeholders) and plays the notify sound. */
    void notifyTarget(CommandContext<CommandSourceStack> ctx, Player target, String key, TagResolver... placeholders) {
        List<TagResolver> all = new ArrayList<>(List.of(placeholders));
        all.add(Placeholder.unparsed("staff", ctx.getSource().getSender().getName()));
        messages.send(target, key, all.toArray(TagResolver[]::new));
        sounds.play(target, SoundEvents.UI_NOTIFY);
    }

    private Optional<Player> allowed(CommandContext<CommandSourceStack> ctx, Player target) {
        CommandSender sender = ctx.getSource().getSender();
        if (!isSelf(ctx, target) && !sender.hasPermission(othersPermission)) {
            kit.fail(ctx, area + ".no-permission-others");
            return Optional.empty();
        }
        return Optional.of(target);
    }
}
