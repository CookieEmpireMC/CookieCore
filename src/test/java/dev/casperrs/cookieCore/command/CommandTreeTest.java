package dev.casperrs.cookieCore.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import dev.casperrs.cookieCore.storage.PlayerRecord;
import dev.casperrs.cookieCore.storage.PlayerStore;
import dev.casperrs.cookieCore.text.Messages;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Drives the real command trees through a plain Brigadier dispatcher. Everything that needs a running server
 * ({@code ArgumentTypes.player()}, Paper's own wrapping of custom arguments) is not covered here.
 */
class CommandTreeTest {

    private static final PlayerStore NO_STORE = new PlayerStore() {
        @Override
        public void init() {
        }

        @Override
        public void upsertSeen(UUID uuid, String name, long now) {
        }

        @Override
        public List<PlayerRecord> loadAll() {
            return List.of();
        }

        @Override
        public Optional<PlayerRecord> findByName(String name) {
            return Optional.empty();
        }

        @Override
        public void close() {
        }
    };

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
        sounds = new SoundService(log.logger, new java.util.Random(1), p -> false);
        sounds.load(SoundConfigParser.parse(YamlFiles.toMap(TestSupport.resourceYaml("sounds.yml")), p -> { }));
        directory = new PlayerDirectory(NO_STORE, Runnable::run, Runnable::run, log.logger, () -> 1L);
        directory.start();
        kit = new CommandKit(messages, sounds, directory);
        PluginSettings settings = PluginSettings.from(new org.bukkit.configuration.file.YamlConfiguration(), Path.of("x"), p -> { });
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

    // --- /cookiecore ---------------------------------------------------------------------------------------------

    @Test
    void nothingWorksWithoutPermission() {
        CommandSender nobody = TestSupport.console(new TestSupport.Recorder());
        for (String line : List.of("cookiecore", "cookiecore version", "cookiecore reload")) {
            assertThrows(CommandSyntaxException.class, () -> run(nobody, line), line);
        }
        assertEquals(0, reloads);
    }

    @Test
    void versionNeedsItsOwnPermission() throws Exception {
        TestSupport.Recorder rec = new TestSupport.Recorder();
        CommandSender onlyReload = TestSupport.console(rec, Perm.ADMIN_RELOAD);
        assertThrows(CommandSyntaxException.class, () -> run(onlyReload, "cookiecore version"));
        assertTrue(rec.messages.isEmpty());
    }

    @Test
    void reloadNeedsItsOwnPermission() {
        CommandSender onlyVersion = TestSupport.console(new TestSupport.Recorder(), Perm.ADMIN_VERSION);
        assertThrows(CommandSyntaxException.class, () -> run(onlyVersion, "cookiecore reload"));
        assertEquals(0, reloads);
    }

    @Test
    void versionShowsVersionServerDatabaseAndStorage() throws Exception {
        TestSupport.Recorder rec = new TestSupport.Recorder();
        assertEquals(1, run(TestSupport.console(rec, Perm.ADMIN_VERSION), "cookiecore version"));
        String text = rec.lastPlain();
        assertTrue(text.contains("9.9.9") && text.contains("Cookie Empire") && text.contains("h2") && text.contains("connected"), text);
    }

    @Test
    void rootWithEitherPermissionShowsUsage() throws Exception {
        TestSupport.Recorder rec = new TestSupport.Recorder();
        assertEquals(1, run(TestSupport.console(rec, Perm.ADMIN_RELOAD), "cookiecore"));
        assertTrue(rec.lastPlain().contains("/cookiecore"), rec.lastPlain());
    }

    @Test
    void unknownSubcommandIsRejected() {
        CommandSender admin = TestSupport.console(new TestSupport.Recorder(), Perm.ADMIN_VERSION, Perm.ADMIN_RELOAD);
        assertThrows(CommandSyntaxException.class, () -> run(admin, "cookiecore bogus"));
        assertThrows(CommandSyntaxException.class, () -> run(admin, "cookiecore reload extra"));
    }

    @Test
    void cleanReloadConfirmsWithTheConfirmSoundForPlayers() throws Exception {
        TestSupport.Recorder rec = new TestSupport.Recorder();
        assertEquals(1, run(TestSupport.player(rec, Perm.ADMIN_RELOAD), "cookiecore reload"));
        assertEquals(1, reloads);
        assertEquals("CookieCore » Configuration reloaded.", rec.lastPlain().replace("»", "»").strip().replaceAll("\\s+", " "));
        assertEquals(1, rec.sounds.size());
        assertEquals("minecraft:block.note_block.pling", rec.sounds.getFirst().name().asString());
    }

    @Test
    void reloadWithProblemsListsEachAndPlaysTheErrorSound() throws Exception {
        reloadProblems = List.of("config.yml: database.port 0 is not a valid port; using 3306", "messages.yml: <red>oops</red> & <bold>");
        TestSupport.Recorder rec = new TestSupport.Recorder();
        int result = run(TestSupport.player(rec, Perm.ADMIN_RELOAD), "cookiecore reload");
        List<String> lines = rec.plain();
        assertEquals(3, lines.size(), lines.toString());
        assertTrue(lines.get(0).contains("2 problem(s)"), lines.get(0));
        assertTrue(lines.get(1).contains("database.port 0"));
        assertTrue(lines.get(2).contains("<red>oops</red> & <bold>"), "problem text is shown literally, never parsed: " + lines.get(2));
        assertEquals(1, rec.sounds.size());
        assertEquals("minecraft:block.note_block.bass", rec.sounds.getFirst().name().asString());
        // documented behaviour (review F-06): the command still reports success to Brigadier
        assertEquals(1, result);
    }

    // --- CommandKit.checked --------------------------------------------------------------------------------------

    @Test
    void checkedRunsTheBodyWithThePermission() throws Exception {
        dispatcher.getRoot().addChild(Commands.literal("t").executes(kit.checked("x.y", ctx -> 42)).build());
        assertEquals(42, run(TestSupport.console(new TestSupport.Recorder(), "x.y"), "t"));
    }

    @Test
    void checkedRefusesASenderWithoutThePermissionEvenIfRequiresLetThemIn() throws Exception {
        dispatcher.getRoot().addChild(Commands.literal("t").executes(kit.checked("x.y", ctx -> 42)).build());
        TestSupport.Recorder rec = new TestSupport.Recorder();
        assertEquals(0, run(TestSupport.player(rec), "t"));
        assertTrue(rec.lastPlain().contains("permission"), rec.lastPlain());
        assertEquals("minecraft:block.note_block.bass", rec.sounds.getFirst().name().asString());
    }

    @Test
    void literalHidesTheNodeFromSendersWithoutThePermission() {
        dispatcher.getRoot().addChild(kit.literal("secret", "x.y").executes(c -> 1).build());
        assertThrows(CommandSyntaxException.class, () -> run(TestSupport.console(new TestSupport.Recorder()), "secret"));
    }

    @Test
    void permittedAcceptsAnyOfSeveralPermissions() {
        var pred = CommandKit.permitted("a.b", "c.d");
        assertTrue(pred.test(TestSupport.source(TestSupport.console(new TestSupport.Recorder(), "c.d"))));
        assertFalse(pred.test(TestSupport.source(TestSupport.console(new TestSupport.Recorder(), "e.f"))));
    }

    // --- the argument kit ----------------------------------------------------------------------------------------

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
    void reasonIsRequired() {
        registerMute();
        assertThrows(CommandSyntaxException.class, () -> run(mod(), "mute notch 7d"));
        assertTrue(captured.get() == null);
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

    @Test
    void permissionIsCheckedOnEveryStep() {
        registerMute();
        CommandSender nobody = TestSupport.console(new TestSupport.Recorder());
        assertThrows(CommandSyntaxException.class, () -> run(nobody, "mute notch 7d why"));
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

    @Test
    void playerSuggestionsAreCapped() {
        registerMute();
        for (int i = 0; i < 500; i++) {
            directory.seen(new UUID(1, i), "Player" + i);
        }
        assertEquals(200, suggest("mute p").size());
    }

    @Test
    void durationSuggestionsFilterByPrefix() {
        registerMute();
        assertEquals(List.of("1h", "12h", "1d"), suggest("mute notch 1").stream().sorted((a, b) -> Integer.compare(
                List.of("1h", "12h", "1d").indexOf(a), List.of("1h", "12h", "1d").indexOf(b))).toList());
        assertEquals(List.of(), suggest("mute notch x"));
        List<String> all = new ArrayList<>(suggest("mute notch "));
        assertTrue(all.containsAll(List.of("30m", "1h", "12h", "1d", "7d", "30d")), all.toString());
    }

    @Test
    void theClientSeesPlainWordsForTheCustomArguments() {
        assertTrue(new KnownPlayerArgument(directory, messages).getNativeType() instanceof com.mojang.brigadier.arguments.StringArgumentType);
        assertTrue(new DurationArgument(messages).getNativeType() instanceof com.mojang.brigadier.arguments.StringArgumentType);
    }
}
