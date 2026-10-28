package dev.casperrs.cookieCore.moderation;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.casperrs.cookieCore.Perm;
import dev.casperrs.cookieCore.command.CommandKit;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

/** {@code /history (player) [page]}: the punishments of any player Core has seen, newest first. */
public final class HistoryCommand {

    private static final String TARGET = "target";
    private static final String PAGE = "page";

    private final CommandKit kit;
    private final PunishmentHistory history;

    public HistoryCommand(CommandKit kit, PunishmentHistory history) {
        this.kit = kit;
        this.history = history;
    }

    public LiteralCommandNode<CommandSourceStack> node() {
        return kit.literal("history", Perm.STAFF_HISTORY)
                .executes(ctx -> kit.say(ctx, "moderation.usage.history"))
                .then(kit.knownPlayer(TARGET)
                        .executes(kit.checked(Perm.STAFF_HISTORY,
                                ctx -> history.show(ctx.getSource().getSender(), CommandKit.player(ctx, TARGET), 1)))
                        .then(Commands.argument(PAGE, IntegerArgumentType.integer(1))
                                .executes(kit.checked(Perm.STAFF_HISTORY,
                                        ctx -> history.show(ctx.getSource().getSender(), CommandKit.player(ctx, TARGET),
                                                IntegerArgumentType.getInteger(ctx, PAGE))))))
                .build();
    }
}
