package dev.casperrs.cookieCore.scoreboard;

import dev.casperrs.cookieCore.text.Template;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A {@link ScoreboardLayout} with every line parsed once. {@code keys} are the placeholders the layout uses, so
 * resolvers nobody asked for are never called. Compiled again only when the layout or the set of registered keys changes.
 */
record CompiledLayout(ScoreboardLayout source, int registryVersion, Template title, List<Template> lines, List<String> keys) {

    static CompiledLayout compile(ScoreboardLayout source, int registryVersion, Collection<String> knownKeys) {
        Template title = Template.compile(source.title(), knownKeys);
        List<Template> lines = source.lines().stream().map(line -> Template.compile(line, knownKeys)).toList();
        Set<String> used = new LinkedHashSet<>(title.keys());
        lines.forEach(line -> used.addAll(line.keys()));
        return new CompiledLayout(source, registryVersion, title, lines, List.copyOf(used));
    }
}
