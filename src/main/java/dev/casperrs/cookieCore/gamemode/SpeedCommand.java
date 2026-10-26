package dev.casperrs.cookieCore.gamemode;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.casperrs.cookieCore.Perm;
import dev.casperrs.cookieCore.command.CommandKit;
import dev.casperrs.cookieCore.sound.SoundService;
import dev.casperrs.cookieCore.text.Messages;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Function;

/** {@code /speed <1-10|reset> [walk|fly] [player]}; without walk or fly it changes the speed of what the player is doing. */
public final class SpeedCommand {

    private static final String TARGET = "target";
    private static final String LEVEL = "level";

    private enum Kind {
        AUTO, WALK, FLY;

        Kind resolve(Player player) {
            return this != AUTO ? this : player.isFlying() ? FLY : WALK;
        }
    }

    /** The speed level from the command line; empty means reset. */
    private interface Level extends Function<CommandContext<CommandSourceStack>, OptionalInt> {
    }

    private final CommandKit kit;
    private final Messages messages;
    private final PlayerTargets targets;

    public SpeedCommand(CommandKit kit, Messages messages, SoundService sounds) {
        this.kit = kit;
        this.messages = messages;
        this.targets = new PlayerTargets(kit, messages, sounds, "speed", Perm.SPEED_OTHERS);
    }

    public LiteralCommandNode<CommandSourceStack> node() {
        Level reset = ctx -> OptionalInt.empty();
        Level number = ctx -> OptionalInt.of(IntegerArgumentType.getInteger(ctx, LEVEL));
        return kit.literal("speed", Perm.SPEED)
                .executes(ctx -> kit.say(ctx, "speed.usage"))
                .then(options(Commands.literal("reset"), reset))
                .then(options(Commands.argument(LEVEL, IntegerArgumentType.integer()).suggests((ctx, builder) -> {
                    for (int level = SpeedScale.MIN_LEVEL; level <= SpeedScale.MAX_LEVEL; level++) {
                        if (String.valueOf(level).startsWith(builder.getRemaining())) {
                            builder.suggest(level);
                        }
                    }
                    return builder.buildFuture();
                }), number))
                .build();
    }

    /** Attaches the optional {@code [walk|fly] [player]} tail to {@code base}. */
    private <B extends ArgumentBuilder<CommandSourceStack, B>> B options(B base, Level level) {
        base.executes(run(level, Kind.AUTO, false));
        base.then(kit.onlinePlayer(TARGET).executes(run(level, Kind.AUTO, true)));
        base.then(Commands.literal("walk")
                .executes(run(level, Kind.WALK, false))
                .then(kit.onlinePlayer(TARGET).executes(run(level, Kind.WALK, true))));
        base.then(Commands.literal("fly")
                .executes(run(level, Kind.FLY, false))
                .then(kit.onlinePlayer(TARGET).executes(run(level, Kind.FLY, true))));
        return base;
    }

    private Command<CommandSourceStack> run(Level level, Kind kind, boolean named) {
        return kit.checked(Perm.SPEED, ctx -> {
            OptionalInt requested = level.apply(ctx);
            if (requested.isPresent() && !SpeedScale.valid(requested.getAsInt())) {
                return kit.fail(ctx, "speed.invalid-level", Placeholder.unparsed("input", String.valueOf(requested.getAsInt())));
            }
            Optional<Player> target = named ? targets.named(ctx, TARGET) : targets.self(ctx);
            return target.map(player -> apply(ctx, player, requested, kind)).orElse(0);
        });
    }

    private int apply(CommandContext<CommandSourceStack> ctx, Player target, OptionalInt level, Kind kind) {
        Kind actual = kind.resolve(target);
        boolean reset = level.isEmpty();
        if (actual == Kind.FLY) {
            target.setFlySpeed(reset ? SpeedScale.FLY_DEFAULT : SpeedScale.fly(level.getAsInt()));
        } else {
            target.setWalkSpeed(reset ? SpeedScale.WALK_DEFAULT : SpeedScale.walk(level.getAsInt()));
        }
        var type = Placeholder.component("type", messages.component(actual == Kind.FLY ? "speed.type.fly" : "speed.type.walk"));
        var number = Placeholder.unparsed("level", reset ? "" : String.valueOf(level.getAsInt()));
        String action = reset ? "reset" : "set";
        if (PlayerTargets.isSelf(ctx, target)) {
            return kit.done(ctx, "speed." + action + "-self", type, number);
        }
        targets.notifyTarget(ctx, target, "speed." + action + "-by", type, number);
        return kit.done(ctx, "speed." + action + "-other", type, number, PlayerTargets.player(target));
    }
}
