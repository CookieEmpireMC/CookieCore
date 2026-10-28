package dev.casperrs.cookieCore.chat;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.casperrs.cookieCore.Perm;
import dev.casperrs.cookieCore.command.CommandKit;
import dev.casperrs.cookieCore.moderation.MuteList;
import dev.casperrs.cookieCore.sound.SoundEvents;
import dev.casperrs.cookieCore.sound.SoundService;
import dev.casperrs.cookieCore.text.Messages;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

import java.util.UUID;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * {@code /msg (player) (message)} and {@code /r (message)}. Registered as plain literals, so Paper replaces the vanilla
 * {@code /msg}, {@code /tell} and {@code /w}. Online players only; the text is shown as typed, never parsed.
 */
public final class MessageCommand {

    private static final String TARGET = "target";
    private static final String MESSAGE = "message";

    private final CommandKit kit;
    private final Messages messages;
    private final SoundService sounds;
    private final ReplyTracker replies;
    private final MuteList mutes;
    private final LongSupplier clock;
    private final Function<UUID, @Nullable Player> online;

    /** {@code online} finds an online player by UUID, null when they are gone. */
    public MessageCommand(CommandKit kit, Messages messages, SoundService sounds, ReplyTracker replies, MuteList mutes,
                          LongSupplier clock, Function<UUID, @Nullable Player> online) {
        this.kit = kit;
        this.messages = messages;
        this.sounds = sounds;
        this.replies = replies;
        this.mutes = mutes;
        this.clock = clock;
        this.online = online;
    }

    public LiteralCommandNode<CommandSourceStack> messageNode() {
        return kit.literal("msg", Perm.CHAT_MSG)
                .executes(ctx -> kit.say(ctx, "chat.usage.msg"))
                .then(kit.onlinePlayer(TARGET)
                        .executes(ctx -> kit.say(ctx, "chat.usage.msg"))
                        .then(kit.reason(MESSAGE).executes(kit.checked(Perm.CHAT_MSG, this::message))))
                .build();
    }

    public LiteralCommandNode<CommandSourceStack> replyNode() {
        return kit.literal("r", Perm.CHAT_MSG)
                .executes(ctx -> kit.say(ctx, "chat.usage.reply"))
                .then(kit.reason(MESSAGE).executes(kit.checked(Perm.CHAT_MSG, this::reply)))
                .build();
    }

    private int message(CommandContext<CommandSourceStack> ctx) {
        Player from = sender(ctx);
        if (from == null) {
            return kit.fail(ctx, "chat.players-only");
        }
        if (muted(from)) {
            return kit.fail(ctx, "moderation.muted-command");
        }
        Player to;
        try {
            to = CommandKit.onlinePlayer(ctx, TARGET);
        } catch (CommandSyntaxException | RuntimeException e) {
            return kit.fail(ctx, "chat.not-online");
        }
        return send(ctx, from, to);
    }

    private int reply(CommandContext<CommandSourceStack> ctx) {
        Player from = sender(ctx);
        if (from == null) {
            return kit.fail(ctx, "chat.players-only");
        }
        if (muted(from)) {
            return kit.fail(ctx, "moderation.muted-command");
        }
        UUID partner = replies.partner(from.getUniqueId());
        if (partner == null) {
            return kit.fail(ctx, "chat.no-reply");
        }
        Player to = online.apply(partner);
        if (to == null) {
            return kit.fail(ctx, "chat.reply-offline");
        }
        return send(ctx, from, to);
    }

    private int send(CommandContext<CommandSourceStack> ctx, Player from, Player to) {
        if (from.getUniqueId().equals(to.getUniqueId())) {
            return kit.fail(ctx, "chat.msg-self");
        }
        String text = CommandKit.reason(ctx, MESSAGE);
        messages.send(from, "chat.msg-to", Placeholder.unparsed("player", to.getName()), Placeholder.unparsed("message", text));
        messages.send(to, "chat.msg-from", Placeholder.unparsed("player", from.getName()), Placeholder.unparsed("message", text));
        sounds.play(to, SoundEvents.CHAT_MESSAGE);
        replies.record(from.getUniqueId(), to.getUniqueId());
        return Command.SINGLE_SUCCESS;
    }

    private static @Nullable Player sender(CommandContext<CommandSourceStack> ctx) {
        return ctx.getSource().getSender() instanceof Player player ? player : null;
    }

    private boolean muted(Player player) {
        return mutes.loaded() && mutes.active(player.getUniqueId(), clock.getAsLong()).isPresent();
    }
}
