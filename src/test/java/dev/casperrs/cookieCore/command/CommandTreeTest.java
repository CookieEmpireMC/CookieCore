package dev.casperrs.cookieCore.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import dev.casperrs.cookieCore.Perm;
import dev.casperrs.cookieCore.TestSupport;
import dev.casperrs.cookieCore.api.PlayerRef;
import dev.casperrs.cookieCore.config.PluginSettings;
import dev.casperrs.cookieCore.config.YamlFiles;
import dev.casperrs.cookieCore.player.PlayerDirectory;
import dev.casperrs.cookieCore.sound.SoundConfigParser;
import dev.casperrs.cookieCore.sound.SoundService;
import dev.casperrs.cookieCore.text.Messages;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Drives the real command trees through a plain Brigadier dispatcher. Paper's own argument wrapping needs a server
 * and is not covered here.
 */
class CommandTreeTest {

    private final TestSupport.LogCapture log = new TestSupport.LogCapture();
    private final Messages messages = new Messages(log.logger);
    private SoundService sounds;
    private PlayerDirectory directory;
    private CommandKit kit;
    private CommandDispatcher<CommandSourceStack> dispatcher;
    private List<String> reloadProblems = List.of();
    private int reloads;

    @BeforeEach
    void setUp() {
        messages.load(TestSupport.resourceYaml("messages.yml"));
        sounds = new SoundService(log.logger, new Random(1), p -> false);
        sounds.load(SoundConfigParser.parse(YamlFiles.toMap(TestSupport.resourceYaml("sounds.yml")), p -> { }));
        directory = new PlayerDirectory(TestSupport.noStore(), Runnable::run, Runnable::run, log.logger, () -> 1L);
        directory.start();
        kit = new CommandKit(messages, sounds, directory);
        PluginSettings settings = PluginSettings.from(new YamlConfiguration(), Path.of("x"), p -> { });
        CookieCoreCommand command = new CookieCoreCommand(kit, messages, sounds, () -> settings, "9.9.9", () -> true, () -> {
            reloads++;
            return reloadProblems;
        });
        dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(command.node());
    }

    private int run(CommandSender who, String line) throws CommandSyntaxException {
        return dispatcher.execute(line, TestSupport.source(who));
    }

    @Test
    void nothingWorksWithoutPermission() {
        CommandSender nobody = TestSupport.console(new TestSupport.Recorder());
        for (String line : List.of("cookiecore", "cookiecore version", "cookiecore reload")) {
            assertThrows(CommandSyntaxException.class, () -> run(nobody, line), line);
        }
        assertEquals(0, reloads);
    }

    @Test
    void versionAndReloadEachNeedTheirOwnPermission() {
        TestSupport.Recorder rec = new TestSupport.Recorder();
        CommandSender onlyReload = TestSupport.console(rec, Perm.ADMIN_RELOAD);
        assertThrows(CommandSyntaxException.class, () -> run(onlyReload, "cookiecore version"));
        assertTrue(rec.messages.isEmpty());
        CommandSender onlyVersion = TestSupport.console(new TestSupport.Recorder(), Perm.ADMIN_VERSION);
        assertThrows(CommandSyntaxException.class, () -> run(onlyVersion, "cookiecore reload"));
        assertEquals(0, reloads);
    }

    @Test
    void cleanReloadConfirmsWithTheConfirmSoundForPlayers() throws Exception {
        TestSupport.Recorder rec = new TestSupport.Recorder();
        assertEquals(1, run(TestSupport.player(rec, Perm.ADMIN_RELOAD), "cookiecore reload"));
        assertEquals(1, reloads);
        assertEquals("CookieCore » Configuration reloaded.", rec.lastPlain().strip());
        assertEquals(1, rec.sounds.size());
        assertEquals("minecraft:block.note_block.pling", rec.sounds.getFirst().name().asString());
    }

    /** F-06: a reload that reported problems still tells Brigadier it failed (0), so command blocks can tell. */
    @Test
    void reloadWithProblemsListsEachAndPlaysTheErrorSound() throws Exception {
        reloadProblems = List.of("config.yml: database.port 0 is not a valid port; using 3306", "messages.yml: <red>oops</red> & <bold>");
        TestSupport.Recorder rec = new TestSupport.Recorder();
        int result = run(TestSupport.player(rec, Perm.ADMIN_RELOAD), "cookiecore reload");
        List<String> lines = rec.plain();
        assertEquals(3, lines.size(), lines.toString());
        assertTrue(lines.get(0).contains("2 problem(s)"), lines.get(0));
        assertTrue(lines.get(2).contains("<red>oops</red> & <bold>"), "problem text is shown literally, never parsed: " + lines.get(2));
        assertEquals(1, rec.sounds.size());
        assertEquals("minecraft:block.note_block.bass", rec.sounds.getFirst().name().asString());
        assertEquals(0, result);
    }

    @Test
    void checkedRefusesASenderWithoutThePermission() throws Exception {
        dispatcher.getRoot().addChild(Commands.literal("t").executes(kit.checked("x.y", ctx -> 42)).build());
        TestSupport.Recorder rec = new TestSupport.Recorder();
        assertEquals(0, run(TestSupport.player(rec), "t"));
        assertTrue(rec.lastPlain().contains("permission"), rec.lastPlain());
        assertEquals("minecraft:block.note_block.bass", rec.sounds.getFirst().name().asString());
    }

    // --- the argument kit: /mute-style command with a player, a duration and a greedy reason ---------------------

    private record Captured(PlayerRef target, Duration time, String reason) {
    }

    private final AtomicReference<Captured> captured = new AtomicReference<>();

    private void registerMute() {
        dispatcher.getRoot().addChild(kit.literal("mute", "m.mute")
                .then(kit.knownPlayer("target")
                        .then(kit.duration("time")
                                .then(kit.reason("reason").executes(kit.checked("m.mute", ctx -> {
                                    captured.set(new Captured(CommandKit.player(ctx, "target"), CommandKit.duration(ctx, "time"),
                                            CommandKit.reason(ctx, "reason")));
                                    return 1;
                                })))))
                .build());
        directory.seen(new UUID(0, 1), "Notch");
        directory.seen(new UUID(0, 2), "Nova");
        directory.seen(new UUID(0, 3), "Alice");
    }

    private CommandSender mod() {
        return TestSupport.console(new TestSupport.Recorder(), "m.mute");
    }

    @Test
    void knownPlayerDurationAndGreedyReasonParse() throws Exception {
        registerMute();
        assertEquals(1, run(mod(), "mute notch 7d12h being very rude  today"));
        Captured c = captured.get();
        assertEquals(new PlayerRef(new UUID(0, 1), "Notch"), c.target());
        assertEquals(Duration.ofDays(7).plusHours(12), c.time());
        assertEquals("being very rude  today", c.reason(), "the reason keeps its spacing");
    }

    @Test
    void reasonWithMiniMessageTagsIsPassedThroughUntouched() throws Exception {
        registerMute();
        run(mod(), "mute Notch 1d <red><click:run_command:/op me>x");
        assertEquals("<red><click:run_command:/op me>x", captured.get().reason());
    }

    @Test
    void unknownPlayerFailsWithTheConfiguredMessage() {
        registerMute();
        CommandSyntaxException e = assertThrows(CommandSyntaxException.class, () -> run(mod(), "mute ghost 7d why"));
        assertTrue(e.getMessage().contains("No player named ghost"), e.getMessage());
        assertTrue(captured.get() == null);
    }

    @Test
    void badDurationFailsWithTheConfiguredMessage() {
        registerMute();
        for (String bad : List.of("7x", "0s", "1h2d", "36501d", "abc")) {
            CommandSyntaxException e = assertThrows(CommandSyntaxException.class, () -> run(mod(), "mute notch " + bad + " why"), bad);
            assertTrue(e.getMessage().contains("'" + bad + "' is not a duration"), e.getMessage());
        }
    }

    private List<String> suggest(String line) {
        var parse = dispatcher.parse(line, TestSupport.source(mod()));
        return dispatcher.getCompletionSuggestions(parse).join().getList().stream().map(Suggestion::getText).toList();
    }

    @Test
    void playerSuggestionsFilterByPrefixCaseInsensitivelyAndSort() {
        registerMute();
        assertEquals(List.of("Alice", "Notch", "Nova"), suggest("mute "));
        assertEquals(List.of("Notch", "Nova"), suggest("mute n"));
        assertEquals(List.of("Notch"), suggest("mute NOT"));
        assertEquals(List.of(), suggest("mute zzz"));
    }

    /** F-01: tab completion is capped so a large directory cannot flood the client or the main thread. */
    @Test
    void playerSuggestionsAreCapped() {
        registerMute();
        for (int i = 0; i < 500; i++) {
            directory.seen(new UUID(1, i), "Player" + i);
        }
        assertEquals(200, suggest("mute p").size());
    }
}
