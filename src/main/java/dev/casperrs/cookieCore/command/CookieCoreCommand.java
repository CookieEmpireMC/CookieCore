package dev.casperrs.cookieCore.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.casperrs.cookieCore.Perm;
import dev.casperrs.cookieCore.config.PluginSettings;
import dev.casperrs.cookieCore.sound.SoundEvents;
import dev.casperrs.cookieCore.sound.SoundService;
import dev.casperrs.cookieCore.text.Messages;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** {@code /cookiecore [version|reload]}: the plugin's own admin commands. */
public final class CookieCoreCommand {

    /** Result of {@code /cookiecore reload}: the problems found (empty = all good). */
    @FunctionalInterface
    public interface Reloader {
        List<String> reload();
    }

    private final CommandKit kit;
    private final Messages messages;
    private final SoundService sounds;
    private final Supplier<PluginSettings> settings;
    private final String version;
    private final BooleanSupplier storageUp;
    private final Reloader reloader;

    public CookieCoreCommand(CommandKit kit, Messages messages, SoundService sounds, Supplier<PluginSettings> settings,
                             String version, BooleanSupplier storageUp, Reloader reloader) {
        this.kit = kit;
        this.messages = messages;
        this.sounds = sounds;
        this.settings = settings;
        this.version = version;
        this.storageUp = storageUp;
        this.reloader = reloader;
    }

    public LiteralCommandNode<CommandSourceStack> node() {
        return Commands.literal("cookiecore")
                .requires(CommandKit.permitted(Perm.ADMIN_VERSION, Perm.ADMIN_RELOAD))
                .executes(ctx -> kit.say(ctx, "command.usage"))
                .then(kit.literal("version", Perm.ADMIN_VERSION)
                        .executes(kit.checked(Perm.ADMIN_VERSION, this::version)))
                .then(kit.literal("reload", Perm.ADMIN_RELOAD)
                        .executes(kit.checked(Perm.ADMIN_RELOAD, this::reload)))
                .build();
    }

    private int version(CommandContext<CommandSourceStack> ctx) {
        PluginSettings s = settings.get();
        Component storage = messages.component(storageUp.getAsBoolean() ? "command.storage-up" : "command.storage-down");
        return kit.say(ctx, "command.version",
                Placeholder.unparsed("version", version),
                Placeholder.unparsed("server", s.serverName()),
                Placeholder.unparsed("database", s.database().type().name().toLowerCase(Locale.ROOT)),
                Placeholder.component("storage", storage));
    }

    private int reload(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        List<String> problems = reloader.reload();
        if (problems.isEmpty()) {
            return kit.done(ctx, "command.reloaded");
        }
        messages.send(sender, "command.reload-failed", Placeholder.unparsed("count", String.valueOf(problems.size())));
        for (String problem : problems) {
            messages.send(sender, "command.reload-problem", Placeholder.unparsed("problem", problem));
        }
        sounds.play(sender, SoundEvents.UI_ERROR);
        return 0; // the sender has been told; 0 lets /execute store and command blocks see that the reload had problems
    }
}
