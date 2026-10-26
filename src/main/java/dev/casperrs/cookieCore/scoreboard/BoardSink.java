package dev.casperrs.cookieCore.scoreboard;

import net.kyori.adventure.text.Component;
import org.bukkit.scoreboard.Objective;

/**
 * Writes to one player's sidebar objective. Lines are fixed entries {@code l00} to {@code l14} with unique, descending
 * scores (ties would sort alphabetically); a line changes only through {@code Score#customName}, so nothing is removed
 * and re-added, which is what makes the client flicker.
 */
final class BoardSink implements SidebarSink {

    private static final String[] ENTRIES = new String[ScoreboardLayout.MAX_LINES];

    static {
        for (int i = 0; i < ENTRIES.length; i++) {
            ENTRIES[i] = "l" + (i < 10 ? "0" : "") + i;
        }
    }

    private final Objective objective;
    private int count;

    BoardSink(Objective objective) {
        this.objective = objective;
    }

    @Override
    public void title(Component title) {
        objective.displayName(title);
    }

    @Override
    public void resize(int newCount) {
        for (int i = 0; i < newCount; i++) {
            objective.getScore(ENTRIES[i]).setScore(newCount - i);
        }
        for (int i = newCount; i < count; i++) {
            objective.getScore(ENTRIES[i]).resetScore();
        }
        count = newCount;
    }

    @Override
    public void line(int index, Component text) {
        objective.getScore(ENTRIES[index]).customName(text);
    }
}
