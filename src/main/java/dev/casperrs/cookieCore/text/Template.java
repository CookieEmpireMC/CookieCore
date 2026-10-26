package dev.casperrs.cookieCore.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * A MiniMessage string parsed once, with its {@code <key>} placeholders left as markers, so that filling in new values
 * every second costs a tree replace instead of a parse. Text values are inserted as plain text and never parsed, so a
 * player name cannot inject tags; component values go in as they are. Keys that were not known at {@link #compile}
 * time stay in the text as literal {@code <key>}.
 */
public final class Template {

    /** Cannot occur in an admin-written YAML string, so a marker never matches real text. */
    private static final char MARK = '\u0001';

    private final Component compiled;
    private final List<String> keys;

    private Template(Component compiled, List<String> keys) {
        this.compiled = compiled;
        this.keys = keys;
    }

    public static Template compile(String source, Collection<String> knownKeys) {
        TagResolver[] resolvers = knownKeys.stream()
                .map(key -> Placeholder.component(key, Component.text(marker(key))))
                .toArray(TagResolver[]::new);
        Component compiled = Messages.miniMessage().deserialize(source, TagResolver.resolver(resolvers));
        String plain = PlainTextComponentSerializer.plainText().serialize(compiled);
        List<String> used = new ArrayList<>();
        for (String key : knownKeys) {
            if (plain.contains(marker(key))) {
                used.add(key);
            }
        }
        return new Template(compiled, List.copyOf(used));
    }

    /** The placeholders this template actually uses. */
    public List<String> keys() {
        return keys;
    }

    /** A key missing from both maps renders as empty text. */
    public Component render(Map<String, String> text, Map<String, Component> components) {
        Component out = compiled;
        for (String key : keys) {
            Component value = components.get(key);
            if (value == null) {
                String t = text.get(key);
                value = t == null || t.isEmpty() ? Component.empty() : Component.text(t);
            }
            out = out.replaceText(TextReplacementConfig.builder().matchLiteral(marker(key)).replacement(value).build());
        }
        return out;
    }

    private static String marker(String key) {
        return MARK + key + MARK;
    }
}
