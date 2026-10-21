package dev.casperrs.cookieCore.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Small helpers for the plugin's YAML files in its data folder. Reading them is quick, startup/reload only. */
public final class YamlFiles {

    private YamlFiles() {
    }

    /** Thrown when a file exists but isn't valid YAML. */
    public static final class BadYamlException extends Exception {
        public BadYamlException(String file, Throwable cause) {
            super(file + " is not valid YAML: " + cause.getMessage(), cause);
        }
    }

    /**
     * Loads {@code name} from the data folder, first copying the jar's default if the file isn't there.
     * Empty when neither the data folder nor the jar has it.
     */
    public static Optional<YamlConfiguration> load(JavaPlugin plugin, String name) throws BadYamlException {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.isFile() && plugin.getResource(name) != null) {
            plugin.saveResource(name, false);
        }
        return load(file, name);
    }

    /** Loads {@code file} (reported as {@code name}); empty when it doesn't exist. */
    public static Optional<YamlConfiguration> load(File file, String name) throws BadYamlException {
        if (!file.isFile()) {
            return Optional.empty();
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (IOException | InvalidConfigurationException e) {
            throw new BadYamlException(name, e);
        }
        return Optional.of(yaml);
    }

    /** Like {@link #load}, with the jar's copy as defaults so keys added in an update still resolve. */
    public static YamlConfiguration loadWithDefaults(JavaPlugin plugin, String name) throws BadYamlException {
        YamlConfiguration yaml = load(plugin, name).orElseGet(YamlConfiguration::new);
        jarCopy(plugin, name).ifPresent(yaml::setDefaults);
        return yaml;
    }

    public static Optional<YamlConfiguration> jarCopy(JavaPlugin plugin, String name) {
        try (InputStream in = plugin.getResource(name)) {
            if (in == null) {
                return Optional.empty();
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return Optional.of(YamlConfiguration.loadConfiguration(reader));
            }
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /** Section tree as plain nested maps (for the pure-Java parsers). */
    public static Map<String, Object> toMap(ConfigurationSection section) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            out.put(key, value instanceof ConfigurationSection child ? toMap(child) : value);
        }
        return out;
    }
}
