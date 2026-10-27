package dev.casperrs.cookieCore.moderation;

import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.casperrs.cookieCore.Perm;
import dev.casperrs.cookieCore.command.CommandKit;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

/** {@code /unban (player) [-s] [reason]}. Register the one node with the alias {@code pardon}, which replaces the vanilla command. */
public final class UnbanCommand {

    private static final String TARGET = "target";

    private final CommandKit kit;
    private final ModerationService service;
    private final BannedPlayerArgument target;

    UnbanCommand(CommandKit kit, ModerationService service, BannedPlayerArgument target) {
        this.kit = kit;
        this.service = service;
        this.target = target;
    }

    public LiteralCommandNode<CommandSourceStack> node() {
        return kit.literal("unban", Perm.STAFF_UNBAN)
                .executes(ctx -> kit.say(ctx, "moderation.usage.unban"))
                .then(SilentReasonTail.attach(Commands.argument(TARGET, target), kit, Perm.STAFF_UNBAN,
                        (ctx, silent, reason) -> service.unban(ctx.getSource().getSender(), CommandKit.player(ctx, TARGET), silent, reason)))
                .build();
    }
}
