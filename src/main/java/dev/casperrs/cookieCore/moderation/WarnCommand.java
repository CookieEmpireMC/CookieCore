package dev.casperrs.cookieCore.moderation;

import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.casperrs.cookieCore.Perm;
import dev.casperrs.cookieCore.command.CommandKit;
import io.papermc.paper.command.brigadier.CommandSourceStack;

/** {@code /warn (player) [-s] [reason]}: a recorded warning for any player Core has seen. */
public final class WarnCommand {

    private static final String TARGET = "target";

    private final CommandKit kit;
    private final ModerationService service;

    public WarnCommand(CommandKit kit, ModerationService service) {
        this.kit = kit;
        this.service = service;
    }

    public LiteralCommandNode<CommandSourceStack> node() {
        return kit.literal("warn", Perm.STAFF_WARN)
                .executes(ctx -> kit.say(ctx, "moderation.usage.warn"))
                .then(SilentReasonTail.attach(kit.knownPlayer(TARGET), kit, Perm.STAFF_WARN,
                        (ctx, silent, reason) -> service.warn(ctx.getSource().getSender(), CommandKit.player(ctx, TARGET), silent, reason)))
                .build();
    }
}
