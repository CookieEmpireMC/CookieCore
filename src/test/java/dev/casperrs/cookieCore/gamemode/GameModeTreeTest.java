package dev.casperrs.cookieCore.gamemode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import dev.casperrs.cookieCore.Perm;
import dev.casperrs.cookieCore.TestSupport;
import dev.casperrs.cookieCore.command.CommandKit;
import dev.casperrs.cookieCore.config.YamlFiles;
import dev.casperrs.cookieCore.player.PlayerDirectory;
import dev.casperrs.cookieCore.sound.SoundConfigParser;
import dev.casperrs.cookieCore.sound.SoundService;
import dev.casperrs.cookieCore.text.Messages;
import io.papermc.paper.command.brigadier.argument.TestVanillaArgumentProvider;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import org.bukkit.GameMode;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The real /gamemode, /gms-/gmsp, /fly and /speed trees, driven through a plain Brigadier dispatcher. */
class GameModeTreeTest {

    private static final String ERROR_SOUND = "minecraft:block.note_block.bass";
    private static final String CONFIRM_SOUND = "minecraft:block.note_block.pling";
    private static final String NOTIFY_SOUND = "minecraft:entity.experience_orb.pickup";

    private final TestSupport.LogCapture log = new TestSupport.LogCapture();
    private final Messages messages = new Messages(log.logger);
    private final List<Runnable> scheduled = new ArrayList<>();
    private CommandDispatcher<CommandSourceStack> dispatcher;

    @BeforeEach
    void setUp() {
        messages.load(TestSupport.resourceYaml("messages.yml"));
        SoundService sounds = new SoundService(log.logger, new Random(1), p -> false);
        sounds.load(SoundConfigParser.parse(YamlFiles.toMap(TestSupport.resourceYaml("sounds.yml")), p -> { }));
        PlayerDirectory directory = new PlayerDirectory(TestSupport.noStore(), Runnable::run, Runnable::run, log.logger, () -> 1L);
        directory.start();
        CommandKit kit = new CommandKit(messages, sounds, directory);
        GameModeCommand gameMode = new GameModeCommand(kit, messages, sounds);
        dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(gameMode.main());
        dispatcher.getRoot().addChild(gameMode.shortcut("gmc", GameMode.CREATIVE));
        dispatcher.getRoot().addChild(gameMode.shortcut("gmsp", GameMode.SPECTATOR));
        dispatcher.getRoot().addChild(new FlyCommand(kit, messages, sounds, new FlyTracker(scheduled::add)).node());
        dispatcher.getRoot().addChild(new SpeedCommand(kit, messages, sounds).node());
        TestVanillaArgumentProvider.ONLINE.clear();
    }

    private int run(CommandSender who, String line) throws CommandSyntaxException {
        return dispatcher.execute(line, TestSupport.source(who));
    }

    private int run(CommandSender who, Entity executor, String line) throws CommandSyntaxException {
        return dispatcher.execute(line, TestSupport.source(who, executor));
    }

    private Set<String> suggest(CommandSender who, String line) {
        var parse = dispatcher.parse(line, TestSupport.source(who));
        return dispatcher.getCompletionSuggestions(parse).join().getList().stream().map(Suggestion::getText)
                .collect(Collectors.toSet());
    }

    /** A player the target selector can find by name. */
    private static FakePlayer online(String name, GameMode mode, String... permissions) {
        FakePlayer player = new FakePlayer(name, mode, permissions);
        TestVanillaArgumentProvider.ONLINE.put(name, player.player());
        return player;
    }

    @Test
    void rootsAreHiddenFromSendersWithoutTheirNodes() throws Exception {
        FakePlayer creativeOnly = new FakePlayer("Alex", GameMode.SURVIVAL, Perm.GAMEMODE_CREATIVE);
        assertThrows(CommandSyntaxException.class, () -> run(creativeOnly.player(), "gmsp"));
        FakePlayer nobody = new FakePlayer("Nobody", GameMode.SURVIVAL);
        assertThrows(CommandSyntaxException.class, () -> run(nobody.player(), "gamemode creative"));
        assertEquals(1, run(creativeOnly.player(), "gmc"));
        assertEquals(GameMode.CREATIVE, creativeOnly.gameMode);
    }

    @Test
    void completionOffersOnlyTheModesTheSenderMayUse() {
        FakePlayer staff = new FakePlayer("Alex", GameMode.SURVIVAL, Perm.GAMEMODE_CREATIVE, Perm.GAMEMODE_SURVIVAL);
        assertEquals(Set.of("creative", "survival"), suggest(staff.player(), "gamemode "));
        assertEquals(Set.of("survival"), suggest(staff.player(), "gamemode s"), "spectator is hidden, not just unusable");
    }

    @Test
    void choosingTheCurrentModeChangesNothingAndSaysSo() throws Exception {
        FakePlayer player = new FakePlayer("Alex", GameMode.CREATIVE, Perm.GAMEMODE_CREATIVE);
        assertEquals(1, run(player.player(), "gamemode c"));
        assertTrue(player.rec.lastPlain().contains("already in Creative mode"), player.rec.lastPlain());
        assertTrue(player.rec.sounds.isEmpty(), "no error or confirm sound for a no-op");
    }

    @Test
    void changingYourselfNeedsTheNodeOfThatMode() throws Exception {
        FakePlayer player = new FakePlayer("Alex", GameMode.SURVIVAL, Perm.GAMEMODE_SURVIVAL);
        assertEquals(0, run(player.player(), "gamemode creative"));
        assertEquals(GameMode.SURVIVAL, player.gameMode);
        assertTrue(player.rec.lastPlain().contains("permission to use Creative mode"), player.rec.lastPlain());
        assertEquals(ERROR_SOUND, player.rec.sounds.getFirst().name().asString());
    }

    @Test
    void changingSomeoneElseNeedsTheOthersNodeAndTheMode() throws Exception {
        FakePlayer steve = online("Steve", GameMode.SURVIVAL);
        FakePlayer modeOnly = new FakePlayer("Alex", GameMode.SURVIVAL, Perm.GAMEMODE_CREATIVE);
        assertEquals(0, run(modeOnly.player(), "gamemode creative Steve"));
        assertTrue(modeOnly.rec.lastPlain().contains("other players"), modeOnly.rec.lastPlain());

        FakePlayer othersOnly = new FakePlayer("Alex", GameMode.SURVIVAL, Perm.GAMEMODE_OTHERS, Perm.GAMEMODE_SURVIVAL);
        assertEquals(0, run(othersOnly.player(), "gamemode creative Steve"));
        assertTrue(othersOnly.rec.lastPlain().contains("permission to use Creative mode"), othersOnly.rec.lastPlain());
        assertEquals(GameMode.SURVIVAL, steve.gameMode, "neither attempt may change Steve");
    }

    @Test
    void staffChangesAnotherPlayerAndTheTargetIsTold() throws Exception {
        FakePlayer steve = online("Steve", GameMode.SURVIVAL);
        FakePlayer staff = new FakePlayer("Alex", GameMode.SURVIVAL, Perm.GAMEMODE_CREATIVE, Perm.GAMEMODE_OTHERS);
        assertEquals(1, run(staff.player(), "gamemode creative Steve"));
        assertEquals(GameMode.CREATIVE, steve.gameMode);
        assertTrue(steve.rec.lastPlain().contains("Alex set your game mode to Creative"), steve.rec.lastPlain());
        assertEquals(NOTIFY_SOUND, steve.rec.sounds.getFirst().name().asString());
        assertTrue(staff.rec.lastPlain().contains("Steve is now in Creative mode"), staff.rec.lastPlain());
        assertEquals(CONFIRM_SOUND, staff.rec.sounds.getFirst().name().asString());
    }

    @Test
    void anUnknownOrOfflineTargetIsAnErrorMessageNotAnException() throws Exception {
        FakePlayer staff = new FakePlayer("Alex", GameMode.SURVIVAL, Perm.GAMEMODE_CREATIVE, Perm.GAMEMODE_OTHERS);
        assertEquals(0, run(staff.player(), "gamemode creative Ghost"));
        assertTrue(staff.rec.lastPlain().contains("not online"), staff.rec.lastPlain());
        assertEquals(ERROR_SOUND, staff.rec.sounds.getFirst().name().asString());
    }

    @Test
    void theConsoleNeedsATargetButExecuteAsGivesOne() throws Exception {
        FakePlayer steve = online("Steve", GameMode.SURVIVAL);
        TestSupport.Recorder consoleRec = new TestSupport.Recorder();
        CommandSender console = TestSupport.console(consoleRec, Perm.GAMEMODE_CREATIVE, Perm.GAMEMODE_OTHERS);
        assertEquals(0, run(console, "gamemode creative"));
        assertTrue(consoleRec.lastPlain().contains("console has no game mode"), consoleRec.lastPlain());
        assertEquals(1, run(console, steve.player(), "gamemode creative"));
        assertEquals(GameMode.CREATIVE, steve.gameMode);
    }

    @Test
    void speedSetsResetsAndCanTargetAnotherPlayer() throws Exception {
        FakePlayer steve = online("Steve", GameMode.SURVIVAL);
        FakePlayer staff = new FakePlayer("Alex", GameMode.SURVIVAL, Perm.SPEED, Perm.SPEED_OTHERS);
        assertEquals(1, run(staff.player(), "speed 5"));
        assertEquals(SpeedScale.walk(5), staff.walkSpeed);
        assertEquals(1, run(staff.player(), "speed reset fly"));
        assertEquals(SpeedScale.FLY_DEFAULT, staff.flySpeed);
        assertEquals(1, run(staff.player(), "speed 5 walk Steve"));
        assertEquals(SpeedScale.walk(5), steve.walkSpeed);
        assertTrue(steve.rec.lastPlain().contains("Alex set your walking speed to 5"), steve.rec.lastPlain());
    }

    @Test
    void speedZeroAndElevenAreRejectedAndChangeNothing() throws Exception {
        FakePlayer player = new FakePlayer("Alex", GameMode.SURVIVAL, Perm.SPEED);
        float before = player.walkSpeed;
        for (String bad : List.of("speed 0", "speed 11")) {
            assertEquals(0, run(player.player(), bad), bad);
            assertTrue(player.rec.lastPlain().contains("is not a speed"), player.rec.lastPlain());
        }
        assertEquals(before, player.walkSpeed);
    }

    @Test
    void flyTogglesForYouAndSaysWhenCreativeAlreadyFlies() throws Exception {
        FakePlayer player = new FakePlayer("Alex", GameMode.SURVIVAL, Perm.FLY);
        assertEquals(1, run(player.player(), "fly"));
        assertTrue(player.allowFlight);
        assertEquals(1, run(player.player(), "fly"));
        assertFalse(player.allowFlight);

        FakePlayer creative = new FakePlayer("Bea", GameMode.CREATIVE, Perm.FLY);
        assertEquals(1, run(creative.player(), "fly"));
        assertTrue(creative.rec.lastPlain().contains("already fly in Creative mode"), creative.rec.lastPlain());

        online("Steve", GameMode.SURVIVAL);
        assertEquals(0, run(player.player(), "fly Steve"), "toggling someone else needs cookiecore.fly.others");
    }
}
