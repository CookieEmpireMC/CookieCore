package dev.casperrs.cookieCore.moderation;

import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.casperrs.cookieCore.Perm;
import dev.casperrs.cookieCore.command.CommandKit;
import io.papermc.paper.command.brigadier.CommandSourceStack;

/** {@code /mute (player) [-s] [reason]}: permanent, for any player Core has seen. */
public final class MuteCommand {

    private static final String TARGET = "target";

    private final CommandKit kit;
    private final ModerationService service;

    public MuteCommand(CommandKit kit, ModerationService service) {
        this.kit = kit;
        this.service = service;
    }

    public LiteralCommandNode<CommandSourceStack> node() {
        return kit.literal("mute", Perm.STAFF_MUTE)
                .executes(ctx -> kit.say(ctx, "moderation.usage.mute"))
                .then(SilentReasonTail.attach(kit.knownPlayer(TARGET), kit, Perm.STAFF_MUTE,
                        (ctx, silent, reason) -> service.mute(ctx.getSource().getSender(), CommandKit.player(ctx, TARGET), null, silent, reason)))
                .build();
    }
}
