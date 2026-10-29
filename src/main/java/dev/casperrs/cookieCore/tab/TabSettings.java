package dev.casperrs.cookieCore.tab;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** {@code tab.yml} as written: MiniMessage source for the header and footer lines and for one name in the list. */
public record TabSettings(List<String> header, List<String> footer, String nameFormat) {

    static final String DEFAULT_NAME_FORMAT = "<rank_prefix><player>";
    private static final String FILE = "tab.yml";

    /** Never fails: a fault is reported to {@code problems} and replaced by an empty list or the default name format. */
    public static TabSettings parse(Map<String, Object> yaml, Consumer<String> problems) {
        String nameFormat = DEFAULT_NAME_FORMAT;
        if (yaml.get("name-format") instanceof String s && !s.isBlank()) {
            nameFormat = s;
        } else {
            problems.accept(FILE + ": 'name-format' is missing or empty; using " + DEFAULT_NAME_FORMAT);
        }
        return new TabSettings(lines(yaml.get("header"), "header", problems), lines(yaml.get("footer"), "footer", problems), nameFormat);
    }

    /** A list of lines (a single text counts as one line); a missing key is no lines, an empty entry a blank line. */
    private static List<String> lines(@Nullable Object raw, String key, Consumer<String> problems) {
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof String single) {
            return List.of(single);
        }
        if (!(raw instanceof List<?> entries)) {
            problems.accept(FILE + ": '" + key + "' must be a list of lines; showing no " + key);
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object entry : entries) {
            out.add(entry == null ? "" : String.valueOf(entry));
        }
        return List.copyOf(out);
    }
}
