package dev.casperrs.cookieCore.moderation;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.casperrs.cookieCore.Perm;
import dev.casperrs.cookieCore.command.CommandKit;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/** {@code /kick (player) [-s] [reason]}. Registered as a plain literal, so Paper replaces the vanilla {@code /kick}. */
public final class KickCommand {

    private static final String TARGET = "target";

    private final CommandKit kit;
    private final ModerationService service;

    public KickCommand(CommandKit kit, ModerationService service) {
        this.kit = kit;
        this.service = service;
    }

    public LiteralCommandNode<CommandSourceStack> node() {
        return kit.literal("kick", Perm.STAFF_KICK)
                .executes(ctx -> kit.say(ctx, "moderation.usage.kick"))
                .then(SilentReasonTail.attach(kit.onlinePlayer(TARGET), kit, Perm.STAFF_KICK, this::run))
                .build();
    }

    private int run(CommandContext<CommandSourceStack> ctx, boolean silent, @Nullable String reason) {
        Player target;
        try {
            target = CommandKit.onlinePlayer(ctx, TARGET);
        } catch (CommandSyntaxException | RuntimeException e) {
            return kit.fail(ctx, "moderation.error.not-online");
        }
        return service.kickOut(ctx.getSource().getSender(), target, silent, reason);
    }
}
