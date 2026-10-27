package dev.casperrs.cookieCore.scoreboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/** The sidebar as written in {@code scoreboard.yml}: MiniMessage source for the title and 1 to 15 lines. */
public record ScoreboardLayout(String title, List<String> lines) {

    static final int MAX_LINES = 15;
    static final int MAX_TITLE_LENGTH = 128;

    /** Empty when anything is wrong; every problem found is reported, so the admin fixes the file in one go. */
    public static Optional<ScoreboardLayout> parse(Map<String, Object> yaml, Consumer<String> problems) {
        boolean ok = true;
        String title = null;
        if (!(yaml.get("title") instanceof String t) || t.isBlank()) {
            problems.accept("scoreboard.yml: 'title' is missing or empty");
            ok = false;
        } else if (t.length() > MAX_TITLE_LENGTH) {
            problems.accept("scoreboard.yml: 'title' is longer than " + MAX_TITLE_LENGTH + " characters");
            ok = false;
        } else {
            title = t;
        }

        List<String> lines = new ArrayList<>();
        if (!(yaml.get("lines") instanceof List<?> raw)) {
            problems.accept("scoreboard.yml: 'lines' must be a list of 1 to " + MAX_LINES + " lines");
            ok = false;
        } else if (raw.isEmpty() || raw.size() > MAX_LINES) {
            problems.accept("scoreboard.yml: 'lines' has " + raw.size() + " lines; it needs 1 to " + MAX_LINES);
            ok = false;
        } else {
            for (Object line : raw) {
                lines.add(line == null ? "" : String.valueOf(line));
            }
        }
        return ok ? Optional.of(new ScoreboardLayout(title, List.copyOf(lines))) : Optional.empty();
    }
}
