package dev.casperrs.cookieCore.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.casperrs.cookieCore.Perm;
import dev.casperrs.cookieCore.TestSupport;
import dev.casperrs.cookieCore.chat.ChatFixture.FakePlayer;
import dev.casperrs.cookieCore.command.CommandKit;
import dev.casperrs.cookieCore.config.YamlFiles;
import dev.casperrs.cookieCore.moderation.MuteList;
import dev.casperrs.cookieCore.player.PlayerDirectory;
import dev.casperrs.cookieCore.sound.SoundConfigParser;
import dev.casperrs.cookieCore.sound.SoundService;
import dev.casperrs.cookieCore.storage.MuteRecord;
import dev.casperrs.cookieCore.text.Messages;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.argument.TestVanillaArgumentProvider;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** /msg, /r and /sc through a plain Brigadier dispatcher, with the real reply tracker, mutes and staff channel behind them. */
class ChatCommandsTest {

    private static final long NOW = 1_000_000_000_000L;

    private final TestSupport.LogCapture log = new TestSupport.LogCapture();
    private final Messages messages = ChatFixture.shippedMessages();
    private final SoundService sounds = new SoundService(log.logger, new Random(1), p -> false);
    private final CommandKit kit;
    private final MuteList mutes = new MuteList();
    private final ReplyTracker replies = new ReplyTracker();
    private final Map<UUID, Player> online = new HashMap<>();
    private final List<FakePlayer> everyone = new ArrayList<>();
    private final StaffChat staff;
    private final CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();

    ChatCommandsTest() {
        sounds.load(SoundConfigParser.parse(YamlFiles.toMap(TestSupport.resourceYaml("sounds.yml")), p -> { }));
        PlayerDirectory directory = new PlayerDirectory(TestSupport.noStore(), Runnable::run, Runnable::run, log.logger, () -> 1L);
        directory.start();
        kit = new CommandKit(messages, sounds, directory);
        mutes.markLoaded();
        ChatConfig config = ChatFixture.shippedConfig();
        config.load(new ArrayList<>());
        RankSource noRanks = player -> Optional.empty();
        staff = new StaffChat(config, messages, noRanks, () -> ChatFixture.SERVER,
                () -> everyone.stream().filter(p -> p.player.hasPermission(Perm.STAFF_CHAT)).map(p -> p.player).toList());
        MessageCommand message = new MessageCommand(kit, messages, sounds, replies, mutes, () -> NOW, online::get);
        dispatcher.getRoot().addChild(message.messageNode());
        dispatcher.getRoot().addChild(message.replyNode());
        dispatcher.getRoot().addChild(new StaffChatCommand(kit, staff).node());
    }

    @BeforeEach
    void clearVanillaSelector() {
        TestVanillaArgumentProvider.ONLINE.clear();
    }

    private FakePlayer join(String name, String... permissions) {
        FakePlayer p = new FakePlayer(name, permissions);
        everyone.add(p);
        online.put(p.id, p.player);
        TestVanillaArgumentProvider.ONLINE.put(name, p.player);
        return p;
    }

    private void leave(FakePlayer p) {
        online.remove(p.id);
        TestVanillaArgumentProvider.ONLINE.remove(p.name);
    }

    private void run(FakePlayer as, String line) throws CommandSyntaxException {
        dispatcher.execute(line, TestSupport.source(as.player));
    }

    private static String last(FakePlayer p) {
        return p.rec.lastPlain();
    }

    /** Both sides get the line, the text is shown as typed, and {@code /r} answers the last partner. */
    @Test
    void msgReachesBothSidesLiterallyAndReplyGoesBack() throws Exception {
        FakePlayer steve = join("Steve", Perm.CHAT_MSG);
        FakePlayer alex = join("Alex", Perm.CHAT_MSG);
        run(steve, "msg Alex <red>hi &c<bold>x");
        assertEquals(List.of("to Alex: <red>hi &c<bold>x"), steve.rec.plain());
        assertEquals(List.of("from Steve: <red>hi &c<bold>x"), alex.rec.plain());
        assertEquals(1, alex.rec.sounds.size(), "the target hears the quiet chime");

        run(alex, "r yo");
        assertEquals("from Alex: yo", last(steve));
    }

    /** A muted player is refused both for /msg and /r, and nothing reaches the target. */
    @Test
    void aMutedPlayerCannotWhisperOrReply() throws Exception {
        FakePlayer steve = join("Steve", Perm.CHAT_MSG);
        FakePlayer alex = join("Alex", Perm.CHAT_MSG);
        mutes.put(new MuteRecord(1L, steve.id, "Steve", "Staff", "spam", NOW - 1_000L, null));
        run(steve, "msg Alex hi");
        assertTrue(last(steve).contains("You are muted"), steve.rec.plain().toString());
        run(steve, "r hi");
        assertTrue(last(steve).contains("You are muted"), steve.rec.plain().toString());
        assertTrue(alex.rec.messages.isEmpty(), "nothing reached the target");
    }

    /** Self, an offline target, no reply partner and a partner who left each get their own clear line. */
    @Test
    void selfOfflineAndMissingPartnersAreRefusedWithAClearLine() throws Exception {
        FakePlayer steve = join("Steve", Perm.CHAT_MSG);
        FakePlayer alex = join("Alex", Perm.CHAT_MSG);
        run(steve, "msg Steve hi");
        assertTrue(last(steve).contains("cannot message yourself"), steve.rec.plain().toString());
        run(steve, "r hi");
        assertTrue(last(steve).contains("nobody to reply to"), steve.rec.plain().toString());

        run(steve, "msg Alex hi");
        leave(alex);
        run(steve, "msg Alex again");
        assertTrue(last(steve).contains("not online"), steve.rec.plain().toString());
        run(steve, "r again");
        assertTrue(last(steve).contains("no longer online"), steve.rec.plain().toString());
    }

    /** {@code /sc} alone toggles the channel; a line with text goes to staff only, and tags typed by staff stay literal. */
    @Test
    void staffChannelToggleAndLinesReachOnlyStaff() throws Exception {
        FakePlayer staffer = join("Staffer", Perm.STAFF_CHAT);
        FakePlayer alex = join("Alex");
        run(staffer, "sc");
        assertTrue(last(staffer).contains("Staff chat is on"), staffer.rec.plain().toString());
        run(staffer, "sc hi <red>x");
        assertEquals("[Staff] Staffer » hi <red>x", last(staffer));
        assertTrue(alex.rec.messages.isEmpty(), "a player without the node never sees staff chat");
        run(staffer, "sc");
        assertTrue(last(staffer).contains("Staff chat is off"), staffer.rec.plain().toString());
    }

    /** The mute stops public chat and private messages, not the staff channel. */
    @Test
    void aMutedStaffMemberCanStillUseStaffChat() throws Exception {
        FakePlayer staffer = join("Staffer", Perm.STAFF_CHAT, Perm.CHAT_MSG);
        mutes.put(new MuteRecord(2L, staffer.id, "Staffer", "Boss", "spam", NOW - 1_000L, null));
        run(staffer, "sc still here");
        assertEquals("[Staff] Staffer » still here", last(staffer));
    }

    /** The reply target is the last player who messaged, and a leaver's own entry goes away. */
    @Test
    void replyTargetIsTheLastPartnerAndQuitDropsIt() {
        UUID steve = UUID.randomUUID();
        UUID alex = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        replies.record(alex, steve);
        replies.record(bob, steve);
        assertEquals(bob, replies.partner(steve));
        replies.forget(steve);
        assertNull(replies.partner(steve));
    }
}
