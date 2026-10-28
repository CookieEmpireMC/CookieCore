package dev.casperrs.cookieCore.chat;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.casperrs.cookieCore.Perm;
import dev.casperrs.cookieCore.command.CommandKit;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.entity.Player;

/** {@code /sc (message)} sends one line to the staff channel; {@code /sc} alone switches your chat to it and back. A muted staff member may use both. */
public final class StaffChatCommand {

    private static final String MESSAGE = "message";

    private final CommandKit kit;
    private final StaffChat staff;

    public StaffChatCommand(CommandKit kit, StaffChat staff) {
        this.kit = kit;
        this.staff = staff;
    }

    public LiteralCommandNode<CommandSourceStack> node() {
        return kit.literal("sc", Perm.STAFF_CHAT)
                .executes(kit.checked(Perm.STAFF_CHAT, this::toggle))
                .then(kit.reason(MESSAGE).executes(kit.checked(Perm.STAFF_CHAT, this::say)))
                .build();
    }

    private int toggle(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getSender() instanceof Player player)) {
            return kit.fail(ctx, "chat.players-only");
        }
        return staff.toggle(player.getUniqueId()) ? kit.done(ctx, "chat.staff-on") : kit.done(ctx, "chat.staff-off");
    }

    private int say(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getSender() instanceof Player player)) {
            return kit.fail(ctx, "chat.players-only");
        }
        staff.say(player, CommandKit.reason(ctx, MESSAGE));
        return Command.SINGLE_SUCCESS;
    }
}
