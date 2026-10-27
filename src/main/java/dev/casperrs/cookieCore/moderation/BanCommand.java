package dev.casperrs.cookieCore.moderation;

import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.casperrs.cookieCore.Perm;
import dev.casperrs.cookieCore.command.CommandKit;
import io.papermc.paper.command.brigadier.CommandSourceStack;

/** {@code /ban (player) [-s] [reason]}: permanent, for any player Core has seen. Replaces the vanilla {@code /ban}. */
public final class BanCommand {

    private static final String TARGET = "target";

    private final CommandKit kit;
    private final ModerationService service;

    public BanCommand(CommandKit kit, ModerationService service) {
        this.kit = kit;
        this.service = service;
    }

    public LiteralCommandNode<CommandSourceStack> node() {
        return kit.literal("ban", Perm.STAFF_BAN)
                .executes(ctx -> kit.say(ctx, "moderation.usage.ban"))
                .then(SilentReasonTail.attach(kit.knownPlayer(TARGET), kit, Perm.STAFF_BAN,
                        (ctx, silent, reason) -> service.ban(ctx.getSource().getSender(), CommandKit.player(ctx, TARGET), null, silent, reason)))
                .build();
    }
}
