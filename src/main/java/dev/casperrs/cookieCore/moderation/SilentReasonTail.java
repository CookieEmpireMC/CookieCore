package dev.casperrs.cookieCore.moderation;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.casperrs.cookieCore.command.CommandKit;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.jspecify.annotations.Nullable;

/**
 * The end shared by every moderation command: nothing, a reason, {@code -s}, or {@code -s} and a reason. The {@code -s}
 * literal is a sibling of the greedy reason, and Brigadier tries a matching literal first, so a reason that itself
 * starts with the bare word {@code -s} is always read as the flag.
 */
final class SilentReasonTail {

    @FunctionalInterface
    interface Action {
        int run(CommandContext<CommandSourceStack> ctx, boolean silent, @Nullable String reason);
    }

    private SilentReasonTail() {
    }

    /** Adds the four endings to {@code parent} (the last argument before them); each runs {@code action} after a permission check. */
    static <B extends ArgumentBuilder<CommandSourceStack, B>> B attach(B parent, CommandKit kit, String permission, Action action) {
        return parent
                .executes(kit.checked(permission, ctx -> action.run(ctx, false, null)))
                .then(Commands.literal("-s")
                        .executes(kit.checked(permission, ctx -> action.run(ctx, true, null)))
                        .then(kit.reason("reason")
                                .executes(kit.checked(permission, ctx -> action.run(ctx, true, CommandKit.reason(ctx, "reason"))))))
                .then(kit.reason("reason")
                        .executes(kit.checked(permission, ctx -> action.run(ctx, false, CommandKit.reason(ctx, "reason")))));
    }
}
