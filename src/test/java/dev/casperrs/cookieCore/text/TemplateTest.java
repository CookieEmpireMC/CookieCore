package dev.casperrs.cookieCore.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class TemplateTest {

    private static final MiniMessage MM = Messages.miniMessage();
    private static final List<String> KEYS = List.of("player", "ping");

    private static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    /** The filled-in template is the same sentence a real parse would give, both as text and as MiniMessage. */
    @Test
    void renderedTextEqualsARealParseOfTheSameSentence() {
        Template t = Template.compile("<gold>Hi <player></gold>, ping <ping>ms", KEYS);
        Component out = t.render(Map.of("player", "Steve", "ping", "42"), Map.of());
        Component real = MM.deserialize("<gold>Hi Steve</gold>, ping 42ms");
        assertEquals(plain(real), plain(out));
        assertEquals(MM.serialize(real), MM.serialize(out));
    }

    /** A player name with tags shows literally and no tag from it takes effect (no click, no colour of its own). */
    @Test
    void playerInputIsNeverReadAsMiniMessage() {
        Template t = Template.compile("<red>Hi <player></red>", KEYS);
        Component out = t.render(Map.of("player", "<red><click:run_command:'/op me'>Evil"), Map.of());
        assertEquals("Hi <red><click:run_command:'/op me'>Evil", plain(out));
        assertNoClickEvents(out);
    }

    /** Keys nobody knew at compile time stay literal; a known key with no value renders empty; components go in as they are. */
    @Test
    void valuesAreFilledInAndOnlyTheKeysTheTemplateUsesAreListed() {
        Template t = Template.compile("a<player>b<ping>c<other>", List.of("player"));
        assertEquals(List.of("player"), t.keys());
        assertEquals("ab<ping>c<other>", plain(t.render(Map.of(), Map.of())));
        Component rich = Component.text("Rich").color(NamedTextColor.GOLD);
        assertEquals("aRichb<ping>c<other>", plain(t.render(Map.of(), Map.of("player", rich))));
    }

    private static void assertNoClickEvents(Component c) {
        assertNull(c.clickEvent());
        c.children().forEach(TemplateTest::assertNoClickEvents);
    }
}
