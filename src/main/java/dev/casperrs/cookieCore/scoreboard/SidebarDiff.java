package dev.casperrs.cookieCore.scoreboard;

import net.kyori.adventure.text.Component;
import org.jspecify.annotations.Nullable;

import java.util.List;

/** Remembers what a player's sidebar shows and writes only what changed, so a tick with nothing new sends nothing. */
final class SidebarDiff {

    private @Nullable Component title;
    private Component[] lines = new Component[0];

    /** Returns how many updates were sent. */
    int apply(SidebarSink sink, Component newTitle, List<Component> newLines) {
        int writes = 0;
        if (newLines.size() != lines.length) {
            sink.resize(newLines.size());
            lines = new Component[newLines.size()];
            writes++;
        }
        if (!newTitle.equals(title)) {
            sink.title(newTitle);
            title = newTitle;
            writes++;
        }
        for (int i = 0; i < lines.length; i++) {
            Component next = newLines.get(i);
            if (!next.equals(lines[i])) {
                sink.line(i, next);
                lines[i] = next;
                writes++;
            }
        }
        return writes;
    }
}
