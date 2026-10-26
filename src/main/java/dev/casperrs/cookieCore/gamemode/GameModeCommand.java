package dev.casperrs.cookieCore.gamemode;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.casperrs.cookieCore.Perm;
import dev.casperrs.cookieCore.command.CommandKit;
import dev.casperrs.cookieCore.sound.SoundService;
import dev.casperrs.cookieCore.text.Messages;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * {@code /gamemode <mode> [player]} (alias {@code /gm}) and the shortcuts {@code /gms /gmc /gma /gmsp [player]}.
 * Registered as plain literals, so Paper replaces the vanilla {@code /gamemode}; {@code /minecraft:gamemode} stays.
 */
public final class GameModeCommand {

    private static final String TARGET = "target";
    private static final Map<String, GameMode> SHORTCUTS = Map.of(
            "gms", GameMode.SURVIVAL, "gmc", GameMode.CREATIVE, "gma", GameMode.ADVENTURE, "gmsp", GameMode.SPECTATOR);

    private final CommandKit kit;
    private final Messages messages;
    private final GameModeArgument modeArgument;
    private final PlayerTargets targets;

    public GameModeCommand(CommandKit kit, Messages messages, SoundService sounds) {
        this.kit = kit;
        this.messages = messages;
        this.modeArgument = new GameModeArgument(messages);
        this.targets = new PlayerTargets(kit, messages, sounds, "gamemode", Perm.GAMEMODE_OTHERS);
    }

    /** Registers every root. A label another plugin already owns is logged once; the rest keeps working. */
    public void register(Commands commands, Logger log) {
        List<String> missing = new ArrayList<>();
        Set<String> labels = commands.register(main(), "Change a game mode", List.of("gm"));
        for (String wanted : List.of("gamemode", "gm")) {
            if (!labels.contains(wanted)) {
                missing.add(wanted);
            }
        }
        SHORTCUTS.forEach((label, mode) -> {
            if (!commands.register(shortcut(label, mode), "Switch to " + GameModes.name(mode) + " mode").contains(label)) {
                missing.add(label);
            }
        });
        if (!missing.isEmpty()) {
            log.warning("Could not take over /" + String.join(", /", missing)
                    + " (another plugin owns it); the namespaced cookiecore:<command> still works");
        }
    }

    LiteralCommandNode<CommandSourceStack> main() {
        return Commands.literal("gamemode")
                .requires(CommandKit.permitted(GameModes.ALL.stream().map(GameModes::permission).toArray(String[]::new)))
                .executes(ctx -> kit.say(ctx, "gamemode.usage"))
                .then(Commands.argument("mode", modeArgument)
                        .executes(ctx -> changeSelf(ctx, ctx.getArgument("mode", GameMode.class)))
                        .then(kit.onlinePlayer(TARGET)
                                .executes(ctx -> changeNamed(ctx, ctx.getArgument("mode", GameMode.class)))))
                .build();
    }

    LiteralCommandNode<CommandSourceStack> shortcut(String label, GameMode mode) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(label)
                .requires(CommandKit.permitted(GameModes.permission(mode)));
        return root.executes(ctx -> changeSelf(ctx, mode))
                .then(kit.onlinePlayer(TARGET).executes(ctx -> changeNamed(ctx, mode)))
                .build();
    }

    private int changeSelf(CommandContext<CommandSourceStack> ctx, GameMode mode) {
        return mayUse(ctx, mode) ? targets.self(ctx).map(p -> apply(ctx, mode, p)).orElse(0) : denied(ctx, mode);
    }

    private int changeNamed(CommandContext<CommandSourceStack> ctx, GameMode mode) {
        return mayUse(ctx, mode) ? targets.named(ctx, TARGET).map(p -> apply(ctx, mode, p)).orElse(0) : denied(ctx, mode);
    }

    private static boolean mayUse(CommandContext<CommandSourceStack> ctx, GameMode mode) {
        return ctx.getSource().getSender().hasPermission(GameModes.permission(mode));
    }

    private int denied(CommandContext<CommandSourceStack> ctx, GameMode mode) {
        return kit.fail(ctx, "gamemode.no-permission-mode", GameModes.placeholder(messages, mode));
    }

    private int apply(CommandContext<CommandSourceStack> ctx, GameMode mode, Player target) {
        boolean self = PlayerTargets.isSelf(ctx, target);
        if (target.getGameMode() == mode) {
            return kit.say(ctx, self ? "gamemode.already-self" : "gamemode.already-other",
                    GameModes.placeholder(messages, mode), PlayerTargets.player(target));
        }
        target.setGameMode(mode);
        if (target.getGameMode() != mode) {
            return kit.fail(ctx, "gamemode.failed", PlayerTargets.player(target));
        }
        if (self) {
            return kit.done(ctx, "gamemode.changed-self", GameModes.placeholder(messages, mode));
        }
        targets.notifyTarget(ctx, target, "gamemode.changed-by", GameModes.placeholder(messages, mode));
        return kit.done(ctx, "gamemode.changed-other", GameModes.placeholder(messages, mode), PlayerTargets.player(target));
    }
}
