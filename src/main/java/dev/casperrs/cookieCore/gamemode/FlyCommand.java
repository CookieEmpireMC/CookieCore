package dev.casperrs.cookieCore.gamemode;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.casperrs.cookieCore.Perm;
import dev.casperrs.cookieCore.command.CommandKit;
import dev.casperrs.cookieCore.sound.SoundService;
import dev.casperrs.cookieCore.text.Messages;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

/** {@code /fly [player]}: toggles flight outside creative and spectator, where the server already allows it. */
public final class FlyCommand {

    private static final String TARGET = "target";

    private final CommandKit kit;
    private final Messages messages;
    private final FlyTracker tracker;
    private final PlayerTargets targets;

    public FlyCommand(CommandKit kit, Messages messages, SoundService sounds, FlyTracker tracker) {
        this.kit = kit;
        this.messages = messages;
        this.tracker = tracker;
        this.targets = new PlayerTargets(kit, messages, sounds, "fly", Perm.FLY_OTHERS);
    }

    public LiteralCommandNode<CommandSourceStack> node() {
        return kit.literal("fly", Perm.FLY)
                .executes(kit.checked(Perm.FLY, ctx -> targets.self(ctx).map(p -> toggle(ctx, p)).orElse(0)))
                .then(kit.onlinePlayer(TARGET)
                        .executes(kit.checked(Perm.FLY, ctx -> targets.named(ctx, TARGET).map(p -> toggle(ctx, p)).orElse(0))))
                .build();
    }

    private int toggle(CommandContext<CommandSourceStack> ctx, Player target) {
        boolean self = PlayerTargets.isSelf(ctx, target);
        GameMode mode = target.getGameMode();
        if (GameModes.flies(mode)) {
            return kit.say(ctx, self ? "fly.already-flies-self" : "fly.already-flies-other",
                    GameModes.placeholder(messages, mode), PlayerTargets.player(target));
        }
        boolean turnOff = target.getAllowFlight();
        if (turnOff) {
            tracker.disable(target);
        } else {
            tracker.enable(target);
        }
        String state = turnOff ? "disabled" : "enabled";
        if (self) {
            return kit.done(ctx, "fly." + state + "-self");
        }
        targets.notifyTarget(ctx, target, "fly." + state + "-by");
        return kit.done(ctx, "fly." + state + "-other", PlayerTargets.player(target));
    }
}
