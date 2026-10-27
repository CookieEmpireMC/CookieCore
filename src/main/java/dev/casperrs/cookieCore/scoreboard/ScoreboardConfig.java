package dev.casperrs.cookieCore.scoreboard;

import dev.casperrs.cookieCore.config.YamlFiles;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Loads {@code scoreboard.yml}. A file that is broken keeps the last good layout and says exactly what is wrong; on the
 * very first load the jar's copy is used instead, so there is always a sidebar to show.
 */
public final class ScoreboardConfig {

    private static final String FILE = "scoreboard.yml";

    /** Reads the admin's file (empty when it does not exist); throws when it is not valid YAML. */
    @FunctionalInterface
    public interface FileSource {
        Optional<YamlConfiguration> read() throws YamlFiles.BadYamlException;
    }

    private final FileSource file;
    private final Supplier<Optional<YamlConfiguration>> jarCopy;
    private @Nullable ScoreboardLayout layout;

    public ScoreboardConfig(FileSource file, Supplier<Optional<YamlConfiguration>> jarCopy) {
        this.file = file;
        this.jarCopy = jarCopy;
    }

    public void load(List<String> problems) {
        List<String> found = new ArrayList<>();
        Optional<ScoreboardLayout> parsed = Optional.empty();
        try {
            Optional<YamlConfiguration> yaml = file.read();
            if (yaml.isPresent()) {
                parsed = ScoreboardLayout.parse(YamlFiles.toMap(yaml.get()), found::add);
            } else {
                found.add(FILE + " is missing");
            }
        } catch (YamlFiles.BadYamlException e) {
            found.add(e.getMessage());
        }
        problems.addAll(found);
        if (parsed.isPresent()) {
            layout = parsed.get();
            return;
        }
        if (layout != null) {
            problems.add(FILE + ": keeping the previous sidebar");
            return;
        }
        layout = jarCopy.get().flatMap(jar -> ScoreboardLayout.parse(YamlFiles.toMap(jar), problems::add)).orElse(null);
        problems.add(FILE + (layout != null ? ": using the built-in sidebar" : ": no sidebar will be shown"));
    }

    /** Null only when neither the data folder's file nor the jar's copy could be read. */
    @Nullable ScoreboardLayout current() {
        return layout;
    }
}
