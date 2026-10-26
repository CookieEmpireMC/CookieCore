package dev.casperrs.cookieCore.scoreboard;

import dev.casperrs.cookieCore.config.YamlFiles;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Loads {@code scoreboard.yml}. A file that is broken keeps the last good layout and says exactly what is wrong; on the
 * very first load the jar's copy is used instead, so there is always a sidebar to show.
 */
public final class ScoreboardConfig {

    private static final String FILE = "scoreboard.yml";

    private final JavaPlugin plugin;
    private @Nullable ScoreboardLayout layout;

    public ScoreboardConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load(List<String> problems) {
        List<String> found = new ArrayList<>();
        Optional<ScoreboardLayout> parsed = Optional.empty();
        try {
            Optional<YamlConfiguration> yaml = YamlFiles.load(plugin, FILE);
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
        layout = YamlFiles.jarCopy(plugin, FILE).flatMap(jar -> ScoreboardLayout.parse(YamlFiles.toMap(jar), problems::add)).orElse(null);
        problems.add(FILE + (layout != null ? ": using the built-in sidebar" : ": no sidebar will be shown"));
    }

    /** Null only when neither the data folder's file nor the jar's copy could be read. */
    @Nullable ScoreboardLayout current() {
        return layout;
    }
}
