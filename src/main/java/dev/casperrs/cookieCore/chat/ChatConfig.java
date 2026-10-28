package dev.casperrs.cookieCore.chat;

import dev.casperrs.cookieCore.config.YamlFiles;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Loads {@code chat.yml}. A file that is broken keeps the last good settings and says exactly what is wrong; on the very
 * first load the jar's copy is used instead.
 */
public final class ChatConfig {

    private static final String FILE = "chat.yml";

    /** Reads the admin's file (empty when it does not exist); throws when it is not valid YAML. */
    @FunctionalInterface
    public interface FileSource {
        Optional<YamlConfiguration> read() throws YamlFiles.BadYamlException;
    }

    private final FileSource file;
    private final Supplier<Optional<YamlConfiguration>> jarCopy;
    private volatile @Nullable ChatSettings settings;

    public ChatConfig(FileSource file, Supplier<Optional<YamlConfiguration>> jarCopy) {
        this.file = file;
        this.jarCopy = jarCopy;
    }

    public void load(List<String> problems) {
        List<String> found = new ArrayList<>();
        Optional<ChatSettings> parsed = Optional.empty();
        try {
            Optional<YamlConfiguration> yaml = file.read();
            if (yaml.isPresent()) {
                parsed = ChatSettings.parse(YamlFiles.toMap(yaml.get()), found::add);
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
        settings = jarCopy.get().flatMap(jar -> ChatSettings.parse(YamlFiles.toMap(jar), problems::add)).orElse(null);
        problems.add(FILE + (settings != null ? ": using the built-in settings" : ": the chat keeps the vanilla format"));
    }

    /** Null only when neither the data folder's file nor the jar's copy could be read. Any thread. */
    public @Nullable ChatSettings current() {
        return settings;
    }
}
