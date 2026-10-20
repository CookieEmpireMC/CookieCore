package dev.casperrs.cookieCore.config;

import dev.casperrs.cookieCore.storage.DatabaseSettings;
import org.bukkit.configuration.ConfigurationSection;

import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Everything in {@code config.yml}. Reloadable except {@code database}, which needs a restart.
 * Wrong values are reported to {@code problems} and replaced by the default, so the plugin always gets a usable object.
 */
public record PluginSettings(String serverName, DatabaseSettings database, int storageTimeoutSeconds) {

    static final String DEFAULT_SERVER_NAME = "Cookie Empire";
    static final String DEFAULT_PREFIX = "core_";

    /** Same settings with another database block (reload keeps the running database until a restart). */
    public PluginSettings withDatabase(DatabaseSettings db) {
        return new PluginSettings(serverName, db, storageTimeoutSeconds);
    }

    public static PluginSettings from(ConfigurationSection c, Path dataFolder, Consumer<String> problems) {
        String serverName = c.getString("server.name", DEFAULT_SERVER_NAME);
        if (serverName.isBlank()) {
            problems.accept("config.yml: server.name is empty; using '" + DEFAULT_SERVER_NAME + "'");
            serverName = DEFAULT_SERVER_NAME;
        }

        String rawType = c.getString("database.type", "h2");
        DatabaseSettings.Type type = DatabaseSettings.Type.parse(rawType).orElseGet(() -> {
            problems.accept("config.yml: database.type '" + rawType + "' is not h2 or mariadb; using h2");
            return DatabaseSettings.Type.H2;
        });
        String prefix = c.getString("database.table-prefix", DEFAULT_PREFIX);
        if (!prefix.matches(DatabaseSettings.PREFIX_PATTERN)) {
            problems.accept("config.yml: database.table-prefix '" + prefix + "' may only use letters, digits and _ (max 32); using "
                    + DEFAULT_PREFIX);
            prefix = DEFAULT_PREFIX;
        }
        int port = c.getInt("database.port", 3306);
        if (port < 1 || port > 65535) {
            problems.accept("config.yml: database.port " + port + " is not a valid port; using 3306");
            port = 3306;
        }
        DatabaseSettings db = new DatabaseSettings(type,
                dataFolder.resolve(c.getString("database.file", "data/core")),
                c.getString("database.host", "localhost"),
                port,
                c.getString("database.name", "cookiecore"),
                c.getString("database.user", "cookiecore"),
                c.getString("database.password", ""),
                prefix);

        int timeout = c.getInt("database.shutdown-timeout-seconds", 10);
        if (timeout < 1) {
            problems.accept("config.yml: database.shutdown-timeout-seconds must be at least 1; using 10");
            timeout = 10;
        }
        return new PluginSettings(serverName, db, timeout);
    }
}
