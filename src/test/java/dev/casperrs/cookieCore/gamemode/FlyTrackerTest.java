package dev.casperrs.cookieCore.gamemode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.casperrs.cookieCore.Perm;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.GameMode;
import org.junit.jupiter.api.Test;

class FlyTrackerTest {

    private final List<Runnable> scheduled = new ArrayList<>();
    private final FlyTracker tracker = new FlyTracker(scheduled::add);

    /** A player who turned {@code /fly} on in {@code mode}, with the event wired to the tracker. */
    private FakePlayer flyer(GameMode mode) {
        FakePlayer player = new FakePlayer("Steve", mode, Perm.FLY);
        player.modeChanges = tracker::onGameModeChange;
        tracker.enable(player.player());
        return player;
    }

    private void runScheduled() {
        List<Runnable> tasks = List.copyOf(scheduled);
        scheduled.clear();
        tasks.forEach(Runnable::run);
    }

    @Test
    void creativeAndSpectatorNeedNoReapply() {
        FakePlayer player = flyer(GameMode.SURVIVAL);
        player.player().setGameMode(GameMode.CREATIVE);
        player.player().setGameMode(GameMode.SPECTATOR);
        assertTrue(scheduled.isEmpty(), "the server already allows flight there");
    }

    @Test
    void flightComesBackOnTheNextTickAfterASwitchToSurvival() {
        FakePlayer player = flyer(GameMode.ADVENTURE);
        player.onGround = false;
        player.player().setAllowFlight(true);
        player.player().setFlying(true);
        player.player().setGameMode(GameMode.SURVIVAL);
        assertFalse(player.allowFlight, "the server drops allowFlight on a switch to survival");
        assertEquals(1, scheduled.size());
        runScheduled();
        assertTrue(player.allowFlight);
        assertTrue(player.flying, "a player who was flying keeps flying");
    }

    @Test
    void playersWhoNeverTurnedFlyOnAreLeftAlone() {
        FakePlayer player = new FakePlayer("Alex", GameMode.SURVIVAL, Perm.FLY);
        player.modeChanges = tracker::onGameModeChange;
        player.player().setGameMode(GameMode.ADVENTURE);
        assertTrue(scheduled.isEmpty());
    }

    @Test
    void losingThePermissionDropsThePlayerFromFlight() {
        FakePlayer player = flyer(GameMode.SURVIVAL);
        player.rec.permissions.remove(Perm.FLY);
        player.player().setGameMode(GameMode.ADVENTURE);
        runScheduled();
        assertFalse(player.allowFlight);
        player.rec.permissions.add(Perm.FLY);
        player.player().setGameMode(GameMode.SURVIVAL);
        assertTrue(scheduled.isEmpty(), "a player who lost the permission is no longer tracked");
    }

    @Test
    void quitSwitchesSurvivalFlightOffAndForgetsThePlayer() {
        FakePlayer player = flyer(GameMode.SURVIVAL);
        tracker.onQuit(player.quitEvent());
        assertFalse(player.allowFlight, "abilities are saved with the player, so flight must not outlive the session");
        player.player().setGameMode(GameMode.ADVENTURE);
        player.player().setGameMode(GameMode.SURVIVAL);
        assertTrue(scheduled.isEmpty());
    }
}
