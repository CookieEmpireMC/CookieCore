package dev.casperrs.cookieCore.tab;

import dev.casperrs.cookieCore.text.Template;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link TabSettings} with every line parsed once. {@code keys} are the placeholders the templates use, so resolvers nobody
 * asked for are never called. Compiled again only when the settings or the set of registered keys change.
 */
record CompiledTab(TabSettings source, int registryVersion, List<Template> header, List<Template> footer, Template name,
                   List<String> keys) {

    static final String PLAYER = "player";
    static final String SERVER = "server";
    static final String ONLINE = "online";
    static final String MAX = "max";
    static final String PING = "ping";
    static final String TPS = "tps";
    static final String RANK_PREFIX = "rank_prefix";

    /** The placeholders Core itself fills in; a registered key with the same name loses. */
    static final List<String> BUILT_IN = List.of(PLAYER, SERVER, ONLINE, MAX, PING, TPS, RANK_PREFIX);

    static CompiledTab compile(TabSettings source, int registryVersion, Collection<String> knownKeys) {
        List<Template> header = source.header().stream().map(line -> Template.compile(line, knownKeys)).toList();
        List<Template> footer = source.footer().stream().map(line -> Template.compile(line, knownKeys)).toList();
        Template name = Template.compile(source.nameFormat(), knownKeys);
        Set<String> used = new LinkedHashSet<>();
        header.forEach(line -> used.addAll(line.keys()));
        footer.forEach(line -> used.addAll(line.keys()));
        used.addAll(name.keys());
        return new CompiledTab(source, registryVersion, header, footer, name, List.copyOf(used));
    }

    Component renderHeader(Map<String, String> text, Map<String, Component> components) {
        return join(header, text, components);
    }

    Component renderFooter(Map<String, String> text, Map<String, Component> components) {
        return join(footer, text, components);
    }

    Component renderName(Map<String, String> text, Map<String, Component> components) {
        return name.render(text, components);
    }

    private static Component join(List<Template> lines, Map<String, String> text, Map<String, Component> components) {
        return Component.join(JoinConfiguration.newlines(), lines.stream().map(line -> line.render(text, components)).toList());
    }
}
