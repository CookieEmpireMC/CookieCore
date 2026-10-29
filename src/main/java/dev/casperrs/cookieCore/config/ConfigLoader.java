package dev.casperrs.cookieCore.config;

import dev.casperrs.cookieCore.sound.SoundConfigParser;
import dev.casperrs.cookieCore.sound.SoundService;
import dev.casperrs.cookieCore.text.Messages;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Loads {@code config.yml}, {@code messages.yml} and {@code sounds.yml} for enable and reload. A file that cannot be read
 * is reported in {@code problems} and the last good values stay in place; on the very first load the jar's copy (or the
 * built-in defaults) is used instead, so the plugin always has something usable.
 */
public final class ConfigLoader {

    private final JavaPlugin plugin;
    private boolean messagesLoaded;
    private boolean soundsLoaded;

    public ConfigLoader(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** The settings from {@code config.yml}; {@code previous} (may be null on the first load) when the file is broken. */
    public PluginSettings loadSettings(PluginSettings previous, List<String> problems) {
        try {
            YamlConfiguration yaml = YamlFiles.loadWithDefaults(plugin, "config.yml");
            return PluginSettings.from(yaml, plugin.getDataFolder().toPath(), problems::add);
        } catch (YamlFiles.BadYamlException e) {
            problems.add(e.getMessage() + (previous != null ? "; keeping the previous values" : "; using the defaults"));
            return previous != null ? previous
                    : PluginSettings.from(new YamlConfiguration(), plugin.getDataFolder().toPath(), problems::add);
        }
    }

    public void loadMessages(Messages messages, List<String> problems) {
        try {
            YamlConfiguration yaml = YamlFiles.loadWithDefaults(plugin, "messages.yml");
            problems.addAll(Messages.problems(yaml));
            messages.load(yaml);
            messagesLoaded = true;
        } catch (YamlFiles.BadYamlException e) {
            problems.add(e.getMessage() + (messagesLoaded ? "; keeping the previous messages" : "; using the built-in messages"));
            if (!messagesLoaded) {
                messages.load(YamlFiles.jarCopy(plugin, "messages.yml").orElseGet(YamlConfiguration::new));
                messagesLoaded = true;
            }
        }
    }

    public void loadSounds(SoundService sounds, List<String> problems) {
        try {
            Optional<YamlConfiguration> yaml = YamlFiles.load(plugin, "sounds.yml");
            if (yaml.isEmpty()) {
                problems.add("sounds.yml is missing; no sounds will play");
                return;
            }
            sounds.load(SoundConfigParser.parse(YamlFiles.toMap(yaml.get()), problems::add));
            soundsLoaded = true;
        } catch (YamlFiles.BadYamlException e) {
            problems.add(e.getMessage() + (soundsLoaded ? "; keeping the previous sounds" : "; using the built-in sounds"));
            if (!soundsLoaded) {
                YamlFiles.jarCopy(plugin, "sounds.yml")
                        .ifPresent(jar -> sounds.load(SoundConfigParser.parse(YamlFiles.toMap(jar), problems::add)));
                soundsLoaded = true;
            }
        }
    }

    /** Runs a module's config load, logs what it found and adds it to {@code all}. */
    public static void collect(Logger log, Consumer<List<String>> load, List<String> all) {
        List<String> found = new ArrayList<>();
        load.accept(found);
        found.forEach(log::warning);
        all.addAll(found);
    }
}
