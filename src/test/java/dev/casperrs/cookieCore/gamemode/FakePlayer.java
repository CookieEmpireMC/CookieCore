package dev.casperrs.cookieCore.gamemode;

import dev.casperrs.cookieCore.TestSupport;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * A player with the abilities the gamemode code uses. It behaves like the server where that matters: a mode change
 * fires the event before it happens, and survival or adventure drop allowFlight and flying.
 */
final class FakePlayer {

    final TestSupport.Recorder rec = new TestSupport.Recorder();
    final String name;
    final UUID id = UUID.randomUUID();
    GameMode gameMode;
    boolean allowFlight;
    boolean flying;
    boolean onGround = true;
    float walkSpeed = 0.2f;
    float flySpeed = 0.1f;
    /** Called with the event before every mode change, like the server does. */
    Consumer<PlayerGameModeChangeEvent> modeChanges = event -> { };

    private final Player player;

    FakePlayer(String name, GameMode mode, String... permissions) {
        this.name = name;
        this.gameMode = mode;
        this.allowFlight = GameModes.flies(mode);
        this.flying = mode == GameMode.SPECTATOR;
        rec.permissions.addAll(List.of(permissions));
        this.player = (Player) Proxy.newProxyInstance(FakePlayer.class.getClassLoader(), new Class<?>[] {Player.class},
                (proxy, method, args) -> handle(proxy, method.getName(), args, method.getReturnType()));
    }

    Player player() {
        return player;
    }

    /** The three-argument form: the two-argument one makes javac 25.0.4 crash while reading its class file. */
    PlayerQuitEvent quitEvent() {
        return new PlayerQuitEvent(player, Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED);
    }

    private void changeMode(GameMode mode) {
        modeChanges.accept(new PlayerGameModeChangeEvent(player, mode, PlayerGameModeChangeEvent.Cause.PLUGIN, null));
        gameMode = mode;
        if (GameModes.flies(mode)) {
            allowFlight = true;
            flying = mode == GameMode.SPECTATOR;
        } else {
            allowFlight = false;
            flying = false;
        }
    }

    private void setFlightAllowed(boolean allowed) {
        allowFlight = allowed;
        if (!allowed) {
            flying = false;
        }
    }

    private void setFlightOn(boolean on) {
        if (on && !allowFlight) {
            throw new IllegalArgumentException("Player is not allowed to fly (check #getAllowFlight())");
        }
        flying = on;
    }

    private Object handle(Object proxy, String method, Object[] args, Class<?> returnType) {
        return switch (method) {
            case "getUniqueId" -> id;
            case "getName" -> name;
            case "isOnline" -> true;
            case "hasPermission" -> args[0] instanceof String s && rec.permissions.contains(s);
            case "sendMessage" -> {
                if (args[0] instanceof Component c) {
                    rec.messages.add(c);
                }
                yield null;
            }
            case "playSound" -> {
                if (args[0] instanceof Sound s) {
                    rec.sounds.add(s);
                }
                yield null;
            }
            case "getGameMode" -> gameMode;
            case "setGameMode" -> {
                changeMode((GameMode) args[0]);
                yield null;
            }
            case "getAllowFlight" -> allowFlight;
            case "setAllowFlight" -> {
                setFlightAllowed((Boolean) args[0]);
                yield null;
            }
            case "isFlying" -> flying;
            case "setFlying" -> {
                setFlightOn((Boolean) args[0]);
                yield null;
            }
            case "isOnGround" -> onGround;
            case "getWalkSpeed" -> walkSpeed;
            case "setWalkSpeed" -> {
                walkSpeed = (Float) args[0];
                yield null;
            }
            case "getFlySpeed" -> flySpeed;
            case "setFlySpeed" -> {
                flySpeed = (Float) args[0];
                yield null;
            }
            case "equals" -> proxy == args[0];
            case "hashCode" -> System.identityHashCode(proxy);
            case "toString" -> "FakePlayer(" + name + ")";
            default -> TestSupport.defaultValue(returnType);
        };
    }
}
