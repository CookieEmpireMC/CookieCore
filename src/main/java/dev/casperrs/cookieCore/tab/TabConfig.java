package dev.casperrs.cookieCore.tab;

import dev.casperrs.cookieCore.config.YamlFiles;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Loads {@code tab.yml}. A file that is broken keeps the last good settings and says exactly what is wrong; on the very
 * first load the jar's copy is used instead, so there is always a tab list to show.
 */
public final class TabConfig {

    private static final String FILE = "tab.yml";

    /** Reads the admin's file (empty when it does not exist); throws when it is not valid YAML. */
    @FunctionalInterface
    public interface FileSource {
        Optional<YamlConfiguration> read() throws YamlFiles.BadYamlException;
    }

    private final FileSource file;
    private final Supplier<Optional<YamlConfiguration>> jarCopy;
    private @Nullable TabSettings settings;

    public TabConfig(FileSource file, Supplier<Optional<YamlConfiguration>> jarCopy) {
        this.file = file;
        this.jarCopy = jarCopy;
    }

    public void load(List<String> problems) {
        List<String> found = new ArrayList<>();
        Optional<TabSettings> parsed = Optional.empty();
        try {
            Optional<YamlConfiguration> yaml = file.read();
            if (yaml.isPresent()) {
                parsed = Optional.of(TabSettings.parse(YamlFiles.toMap(yaml.get()), found::add));
            } else {
                found.add(FILE + " is missing");
            }
        } catch (YamlFiles.BadYamlException e) {
            found.add(e.getMessage());
        }
        problems.addAll(found);
        if (parsed.isPresent()) {
            settings = parsed.get();
            return;
        }
        if (settings != null) {
            problems.add(FILE + ": keeping the previous settings");
            return;
        }
        settings = jarCopy.get().map(jar -> TabSettings.parse(YamlFiles.toMap(jar), problems::add)).orElse(null);
        problems.add(FILE + (settings != null ? ": using the built-in settings" : ": no tab list will be shown"));
    }

    /** Null only when neither the data folder's file nor the jar's copy could be read. */
    @Nullable TabSettings current() {
        return settings;
    }
}
