package dev.casperrs.cookieCore.moderation;

import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.casperrs.cookieCore.Perm;
import dev.casperrs.cookieCore.command.CommandKit;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

/** {@code /unmute (player) [-s] [reason]}; completion offers only muted players. */
public final class UnmuteCommand {

    private static final String TARGET = "target";

    private final CommandKit kit;
    private final ModerationService service;
    private final MutedPlayerArgument target;

    UnmuteCommand(CommandKit kit, ModerationService service, MutedPlayerArgument target) {
        this.kit = kit;
        this.service = service;
        this.target = target;
    }

    public LiteralCommandNode<CommandSourceStack> node() {
        return kit.literal("unmute", Perm.STAFF_UNMUTE)
                .executes(ctx -> kit.say(ctx, "moderation.usage.unmute"))
                .then(SilentReasonTail.attach(Commands.argument(TARGET, target), kit, Perm.STAFF_UNMUTE,
                        (ctx, silent, reason) -> service.unmute(ctx.getSource().getSender(), CommandKit.player(ctx, TARGET), silent, reason)))
                .build();
    }
}
