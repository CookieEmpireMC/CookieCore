package dev.casperrs.cookieCore.moderation;

import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.casperrs.cookieCore.Perm;
import dev.casperrs.cookieCore.command.CommandKit;
import io.papermc.paper.command.brigadier.CommandSourceStack;

/** {@code /tempmute (player) (duration) [-s] [reason]}, for example {@code /tempmute Steve 2h spam}. */
public final class TempmuteCommand {

    private static final String TARGET = "target";
    private static final String TIME = "time";

    private final CommandKit kit;
    private final ModerationService service;

    public TempmuteCommand(CommandKit kit, ModerationService service) {
        this.kit = kit;
        this.service = service;
    }

    public LiteralCommandNode<CommandSourceStack> node() {
        return kit.literal("tempmute", Perm.STAFF_MUTE)
                .executes(ctx -> kit.say(ctx, "moderation.usage.tempmute"))
                .then(kit.knownPlayer(TARGET)
                        .executes(ctx -> kit.say(ctx, "moderation.usage.tempmute"))
                        .then(SilentReasonTail.attach(kit.duration(TIME), kit, Perm.STAFF_MUTE,
                                (ctx, silent, reason) -> service.mute(ctx.getSource().getSender(), CommandKit.player(ctx, TARGET),
                                        CommandKit.duration(ctx, TIME), silent, reason))))
                .build();
    }
}
