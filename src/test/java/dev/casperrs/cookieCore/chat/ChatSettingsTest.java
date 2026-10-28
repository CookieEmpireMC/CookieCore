package dev.casperrs.cookieCore.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.casperrs.cookieCore.TestSupport;
import dev.casperrs.cookieCore.chat.ChatFixture.FakePlayer;
import dev.casperrs.cookieCore.config.YamlFiles;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ChatSettingsTest {

    private static final String OWNER = "cookiecore.rank.owner";
    private static final String ADMIN = "cookiecore.rank.admin";
    private static final String MEMBER = "cookiecore.rank.member";

    private final List<String> problems = new ArrayList<>();

    private static ChatSettings shipped() {
        List<String> found = new ArrayList<>();
        ChatSettings settings = ChatSettings.parse(YamlFiles.toMap(TestSupport.resourceYaml("chat.yml")), found::add)
                .orElseThrow();
        assertTrue(found.isEmpty(), found.toString());
        return settings;
    }

    /** Every shipped chat.yml text parses with the strict MiniMessage instance (typos and unclosed tags fail here). */
    @Test
    void shippedChatYmlLoadsAndEveryTextParsesStrictly() {
        YamlConfiguration yaml = TestSupport.resourceYaml("chat.yml");
        MiniMessage strict = MiniMessage.builder().strict(true).build();
        List<String> texts = new ArrayList<>(List.of(yaml.getString("format"), yaml.getString("staff-format"), yaml.getString("default-prefix")));
        texts.addAll(yaml.getStringList("motd"));
        for (Map<?, ?> rank : yaml.getMapList("ranks")) {
            texts.add(String.valueOf(rank.get("prefix")));
        }
        for (String text : texts) {
            if (!text.isBlank()) {
                strict.deserialize(text);
            }
        }
        shipped();
    }

    /** A rank is shown in front of the name; with no rank the line still reads right; the player's text stays as typed. */
    @Test
    void chatLineShowsTheRankAndKeepsPlayerTagsAndCodesLiteral() {
        ChatSettings settings = shipped();
        Component message = Component.text("<red>hi &c<bold>x");
        Component withRank = settings.format().render(Map.of("server", ChatFixture.SERVER),
                Map.of("rank_prefix", settings.prefixFor(OWNER::equals).orElseThrow(), "player", Component.text("Steve"),
                        "message", message));
        Component withoutRank = settings.format().render(Map.of("server", ChatFixture.SERVER),
                Map.of("rank_prefix", Component.empty(), "player", Component.text("Steve"), "message", message));
        assertEquals("OWNER Steve » <red>hi &c<bold>x", PlainTextComponentSerializer.plainText().serialize(withRank));
        assertEquals("Steve » <red>hi &c<bold>x", PlainTextComponentSerializer.plainText().serialize(withoutRank));
    }

    /** The first rank the player has wins (list order), then the default prefix, then none. */
    @Test
    void rankOrderIsFirstMatchThenDefaultThenNothing() throws Exception {
        ChatSettings settings = shipped();
        assertEquals("ADMIN ", PlainTextComponentSerializer.plainText()
                .serialize(settings.prefixFor(p -> p.equals(ADMIN) || p.equals(MEMBER)).orElseThrow()));
        assertFalse(settings.prefixFor(p -> false).isPresent(), "no rank and an empty default prefix give no prefix");

        Map<String, Object> withDefault = new HashMap<>(YamlFiles.toMap(TestSupport.resourceYaml("chat.yml")));
        withDefault.put("default-prefix", "<gray>guest ");
        ChatSettings guest = ChatSettings.parse(withDefault, problems::add).orElseThrow();
        assertEquals("guest ", PlainTextComponentSerializer.plainText().serialize(guest.prefixFor(p -> false).orElseThrow()));
    }

    /** LuckPerms' prefix is used when it has one, then chat.yml's rank, so a LuckPerms prefix can override a rank. */
    @Test
    void aLuckPermsPrefixWinsAndTheChatYmlRankIsTheFallback() {
        ChatSettings settings = shipped();
        RankSource chatYml = player -> settings.prefixFor(player::hasPermission);
        RankSource luckPerms = player -> player.getName().equals("Gold") ? Optional.of(Component.text("[LP] ")) : Optional.empty();
        RankSource both = luckPerms.orElse(chatYml);
        FakePlayer gold = new FakePlayer("Gold", ADMIN);
        FakePlayer plain = new FakePlayer("Plain", ADMIN);
        assertEquals("[LP] ", PlainTextComponentSerializer.plainText().serialize(both.prefix(gold.player).orElseThrow()));
        assertEquals("ADMIN ", PlainTextComponentSerializer.plainText().serialize(both.prefix(plain.player).orElseThrow()));
    }

    /** The shipped MOTD fills in the server name, the online count and the maximum, on two lines. */
    @Test
    void theServerListTextFillsInItsPlaceholders() {
        Map<String, String> values = Map.of("server", ChatFixture.SERVER, "online", "3", "max", "20");
        Component text = MotdListener.render(shipped().motd(), values);
        assertEquals("Cookie Empire - an idle clicker you play in the world\n3/20 bakers online",
                PlainTextComponentSerializer.plainText().serialize(text));
    }

    /** A bad part is reported with its exact reason: a template without {@code <message>} stops the file, a bad rank is skipped. */
    @Test
    void aBrokenChatYmlIsReportedAndItsBadPartIsNotUsed() {
        Map<String, Object> noMessage = new HashMap<>(YamlFiles.toMap(TestSupport.resourceYaml("chat.yml")));
        noMessage.put("format", "<player> says hi");
        assertTrue(ChatSettings.parse(noMessage, problems::add).isEmpty());
        assertEquals(List.of("chat.yml: 'format' must contain <message>"), problems);

        problems.clear();
        Map<String, Object> badRank = new HashMap<>(YamlFiles.toMap(TestSupport.resourceYaml("chat.yml")));
        badRank.put("ranks", List.of(Map.of("permission", ADMIN, "prefix", "<red>ADMIN "), Map.of("permission", MEMBER)));
        ChatSettings settings = ChatSettings.parse(badRank, problems::add).orElseThrow();
        assertEquals(1, settings.ranks().size(), "the valid rank is kept");
        assertEquals(List.of("chat.yml: ranks entry 2 needs a 'permission' and a 'prefix'; skipped"), problems);
    }
}
