package dev.casperrs.cookieCore.moderation;

import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.casperrs.cookieCore.Perm;
import dev.casperrs.cookieCore.command.CommandKit;
import io.papermc.paper.command.brigadier.CommandSourceStack;

/** {@code /tempban (player) (duration) [-s] [reason]}, for example {@code /tempban Steve 7d12h spam}. */
public final class TempbanCommand {

    private static final String TARGET = "target";
    private static final String TIME = "time";

    private final CommandKit kit;
    private final ModerationService service;

    public TempbanCommand(CommandKit kit, ModerationService service) {
        this.kit = kit;
        this.service = service;
    }

    public LiteralCommandNode<CommandSourceStack> node() {
        return kit.literal("tempban", Perm.STAFF_BAN)
                .executes(ctx -> kit.say(ctx, "moderation.usage.tempban"))
                .then(kit.knownPlayer(TARGET)
                        .executes(ctx -> kit.say(ctx, "moderation.usage.tempban"))
                        .then(SilentReasonTail.attach(kit.duration(TIME), kit, Perm.STAFF_BAN,
                                (ctx, silent, reason) -> service.ban(ctx.getSource().getSender(), CommandKit.player(ctx, TARGET),
                                        CommandKit.duration(ctx, TIME), silent, reason))))
                .build();
    }
}
