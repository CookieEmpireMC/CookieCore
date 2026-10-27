package io.papermc.paper.command.brigadier.argument;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import io.papermc.paper.command.brigadier.argument.predicate.BlockInWorldPredicate;
import io.papermc.paper.command.brigadier.argument.predicate.ItemStackPredicate;
import io.papermc.paper.command.brigadier.argument.range.DoubleRangeProvider;
import io.papermc.paper.command.brigadier.argument.range.IntegerRangeProvider;
import io.papermc.paper.command.brigadier.argument.resolvers.AngleResolver;
import io.papermc.paper.command.brigadier.argument.resolvers.BlockPositionResolver;
import io.papermc.paper.command.brigadier.argument.resolvers.ColumnBlockPositionResolver;
import io.papermc.paper.command.brigadier.argument.resolvers.ColumnFinePositionResolver;
import io.papermc.paper.command.brigadier.argument.resolvers.FinePositionResolver;
import io.papermc.paper.command.brigadier.argument.resolvers.PlayerProfileListResolver;
import io.papermc.paper.command.brigadier.argument.resolvers.RotationResolver;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.EntitySelectorArgumentResolver;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import io.papermc.paper.entity.LookAnchor;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;

/**
 * Test-only stand-in for Paper's server-side {@code VanillaArgumentProvider}, so {@code ArgumentTypes.player()} works
 * without a server. It must be a real subtype (ServiceLoader's {@code findFirst} rejects anything else), and it has to
 * live in Paper's package because the interface is package-private. The service file in the test resources names it.
 * Only {@link #player()} and {@link #players()} are used; every other argument is {@code null}. Names resolve from
 * {@link #ONLINE}.
 */
public final class TestVanillaArgumentProvider implements VanillaArgumentProvider {

    public static final Map<String, Player> ONLINE = new HashMap<>();

    private static final ArgumentType<PlayerSelectorArgumentResolver> PLAYER = new ArgumentType<>() {
        @Override
        public PlayerSelectorArgumentResolver parse(StringReader reader) {
            return resolver(reader.readUnquotedString());
        }

        @Override
        public Collection<String> getExamples() {
            return List.of("Steve");
        }
    };

    private static PlayerSelectorArgumentResolver resolver(String name) {
        return (PlayerSelectorArgumentResolver) Proxy.newProxyInstance(TestVanillaArgumentProvider.class.getClassLoader(),
                new Class<?>[] {PlayerSelectorArgumentResolver.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "resolve" -> ONLINE.containsKey(name) ? List.of(ONLINE.get(name)) : List.of();
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
    }

    @Override
    public ArgumentType<EntitySelectorArgumentResolver> entity() {
        return null;
    }

    @Override
    public ArgumentType<PlayerSelectorArgumentResolver> player() {
        return PLAYER;
    }

    @Override
    public ArgumentType<EntitySelectorArgumentResolver> entities() {
        return null;
    }

    @Override
    public ArgumentType<PlayerSelectorArgumentResolver> players() {
        return PLAYER;
    }

    @Override
    public ArgumentType<PlayerProfileListResolver> playerProfiles() {
        return null;
    }

    @Override
    public ArgumentType<BlockPositionResolver> blockPosition() {
        return null;
    }

    @Override
    public ArgumentType<ColumnBlockPositionResolver> columnBlockPosition() {
        return null;
    }

    @Override
    public ArgumentType<FinePositionResolver> finePosition(boolean centerIntegers) {
        return null;
    }

    @Override
    public ArgumentType<ColumnFinePositionResolver> columnFinePosition(boolean centerIntegers) {
        return null;
    }

    @Override
    public ArgumentType<RotationResolver> rotation() {
        return null;
    }

    @Override
    public ArgumentType<AngleResolver> angle() {
        return null;
    }

    @Override
    public ArgumentType<AxisSet> axes() {
        return null;
    }

    @Override
    public ArgumentType<BlockState> blockState() {
        return null;
    }

    @Override
    public ArgumentType<BlockInWorldPredicate> blockInWorldPredicate() {
        return null;
    }

    @Override
    public ArgumentType<ItemStack> itemStack() {
        return null;
    }

    @Override
    public ArgumentType<ItemStackPredicate> itemStackPredicate() {
        return null;
    }

    @Override
    public ArgumentType<NamedTextColor> namedColor() {
        return null;
    }

    @Override
    public ArgumentType<TextColor> hexColor() {
        return null;
    }

    @Override
    public ArgumentType<Component> component() {
        return null;
    }

    @Override
    public ArgumentType<Style> style() {
        return null;
    }

    @Override
    public ArgumentType<SignedMessageResolver> signedMessage() {
        return null;
    }

    @Override
    public ArgumentType<DisplaySlot> scoreboardDisplaySlot() {
        return null;
    }

    @Override
    public ArgumentType<NamespacedKey> namespacedKey() {
        return null;
    }

    @Override
    public ArgumentType<Key> key() {
        return null;
    }

    @Override
    public ArgumentType<IntegerRangeProvider> integerRange() {
        return null;
    }

    @Override
    public ArgumentType<DoubleRangeProvider> doubleRange() {
        return null;
    }

    @Override
    public ArgumentType<World> world() {
        return null;
    }

    @Override
    public ArgumentType<GameMode> gameMode() {
        return null;
    }

    @Override
    public ArgumentType<HeightMap> heightMap() {
        return null;
    }

    @Override
    public ArgumentType<UUID> uuid() {
        return null;
    }

    @Override
    public ArgumentType<Criteria> objectiveCriteria() {
        return null;
    }

    @Override
    public ArgumentType<LookAnchor> entityAnchor() {
        return null;
    }

    @Override
    public ArgumentType<Integer> time(int minimum) {
        return null;
    }

    @Override
    public ArgumentType<Mirror> templateMirror() {
        return null;
    }

    @Override
    public ArgumentType<StructureRotation> templateRotation() {
        return null;
    }

    @Override
    public <T> ArgumentType<TypedKey<T>> resourceKey(RegistryKey<T> registryKey) {
        return null;
    }

    @Override
    public <T> ArgumentType<T> resource(RegistryKey<T> registryKey) {
        return null;
    }
}
