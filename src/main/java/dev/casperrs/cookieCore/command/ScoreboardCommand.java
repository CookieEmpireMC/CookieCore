package dev.casperrs.cookieCore.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.casperrs.cookieCore.Perm;
import dev.casperrs.cookieCore.scoreboard.SidebarService;
import dev.casperrs.cookieCore.sound.SoundEvents;
import dev.casperrs.cookieCore.sound.SoundService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.entity.Player;

/** {@code /scoreboard [toggle]} (alias {@code /sb}): hide or show your own sidebar. */
public final class ScoreboardCommand {

    private final CommandKit kit;
    private final SoundService sounds;
    private final SidebarService sidebar;

    public ScoreboardCommand(CommandKit kit, SoundService sounds, SidebarService sidebar) {
        this.kit = kit;
        this.sounds = sounds;
        this.sidebar = sidebar;
    }

    public LiteralCommandNode<CommandSourceStack> node() {
        return Commands.literal("scoreboard")
                .requires(CommandKit.permitted(Perm.SCOREBOARD_TOGGLE))
                .executes(kit.checked(Perm.SCOREBOARD_TOGGLE, this::toggle))
                .then(kit.literal("toggle", Perm.SCOREBOARD_TOGGLE)
                        .executes(kit.checked(Perm.SCOREBOARD_TOGGLE, this::toggle)))
                .build();
    }

    private int toggle(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getSender() instanceof Player player)) {
            return kit.fail(ctx, "scoreboard.players-only");
        }
        return switch (sidebar.toggle(player)) {
            case DISABLED -> kit.fail(ctx, "scoreboard.disabled");
            case SHOWN -> kit.done(ctx, "scoreboard.shown");
            case HIDDEN -> {
                int result = kit.say(ctx, "scoreboard.hidden");
                sounds.play(player, SoundEvents.UI_NOTIFY);
                yield result;
            }
        };
    }
}
