package dev.casperrs.cookieCore.rank;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class RankSettingsTest {

    private static RankSettings ranks(boolean withDefault) {
        Map<String, Object> yaml = new HashMap<>();
        if (withDefault) {
            yaml.put("default-prefix", "<gray>guest ");
        }
        yaml.put("ranks", List.of(Map.of("permission", "admin", "prefix", "<red>ADMIN "),
                Map.of("permission", "member", "prefix", "<green>MEMBER ")));
        return RankSettings.parse(yaml, problem -> {
            throw new AssertionError(problem);
        });
    }

    /** A rank source with a fixed prefix (null = none) and order. */
    private static RankSource source(String prefix, int order) {
        return new RankSource() {
            @Override
            public Optional<Component> prefix(Player player) {
                return Optional.ofNullable(prefix).map(Component::text);
            }

            @Override
            public int order(Player player) {
                return order;
            }
        };
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    @Test
    void theFirstListedRankIsTheBiggestOrderTheDefaultIsZeroAndTheFirstMatchWins() {
        RankSettings settings = ranks(true);
        assertEquals(2, settings.resolve(p -> p.equals("admin")).order());
        assertEquals(1, settings.resolve(p -> p.equals("member")).order());
        assertEquals(2, settings.resolve(p -> true).order(), "admin is listed first");
        RankSettings.Resolved guest = settings.resolve(p -> false);
        assertEquals(0, guest.order());
        assertEquals("guest ", plain(guest.prefix().orElseThrow()));
    }

    @Test
    void withoutRanksOrDefaultThereIsNoPrefixAndNoOrder() {
        RankSettings.Resolved none = ranks(false).resolve(p -> false);
        assertTrue(none.prefix().isEmpty());
        assertEquals(0, none.order());
    }

    @Test
    void aLuckPermsWeightWinsOverTheRankFromChatYml() {
        RankSource combined = source("LP ", 5).orElse(source("ADMIN ", 2));
        assertEquals(5, combined.order(null));
        assertEquals("LP ", plain(combined.prefix(null).orElseThrow()));
    }

    @Test
    void theChatYmlOrderIsUsedWhenLuckPermsKnowsNothingAboutThePlayer() {
        RankSource combined = source(null, 0).orElse(source("ADMIN ", 2));
        assertEquals(2, combined.order(null));
        assertEquals("ADMIN ", plain(combined.prefix(null).orElseThrow()));
    }

    /** M-1 (core-tab review): a group with a prefix but no weight hides the chat.yml order here, unlike a group without a prefix. */
    @Test
    @Disabled("M-1 core-tab: enable after RankSource.orElse uses the chat.yml order when the LuckPerms weight is 0")
    void aLuckPermsGroupWithoutWeightDoesNotHideTheChatYmlOrder() {
        RankSource combined = source("LP ", 0).orElse(source("ADMIN ", 2));
        assertEquals(2, combined.order(null));
    }
}
