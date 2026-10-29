package dev.casperrs.cookieCore.config;

import dev.casperrs.cookieCore.storage.DatabaseSettings;
import org.bukkit.configuration.ConfigurationSection;

import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Everything in {@code config.yml}. Reloadable except {@code database}, which needs a restart.
 * Wrong values are reported to {@code problems} and replaced by the default, so the plugin always gets a usable object.
 */
public record PluginSettings(String serverName, DatabaseSettings database, boolean scoreboardEnabled, boolean chatEnabled,
                             boolean tabEnabled) {

    static final String DEFAULT_SERVER_NAME = "Cookie Empire";
    static final String DEFAULT_PREFIX = "core_";

    public PluginSettings(String serverName, DatabaseSettings database, boolean scoreboardEnabled) {
        this(serverName, database, scoreboardEnabled, true, true);
    }

    public PluginSettings(String serverName, DatabaseSettings database, boolean scoreboardEnabled, boolean chatEnabled) {
        this(serverName, database, scoreboardEnabled, chatEnabled, true);
    }

    /** Same settings with another database block (reload keeps the running database until a restart). */
    public PluginSettings withDatabase(DatabaseSettings db) {
        return new PluginSettings(serverName, db, scoreboardEnabled, chatEnabled, tabEnabled);
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
        int port = intOrDefault(c, "database.port", 3306, problems);
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

        boolean scoreboardEnabled = flag(c, "scoreboard.enabled", problems);
        boolean chatEnabled = flag(c, "chat.enabled", problems);
        boolean tabEnabled = flag(c, "tab.enabled", problems);

        return new PluginSettings(serverName, db, scoreboardEnabled, chatEnabled, tabEnabled);
    }

    /** A true/false switch that defaults to true. */
    private static boolean flag(ConfigurationSection c, String path, Consumer<String> problems) {
        Object raw = c.get(path);
        if (raw instanceof Boolean b) {
            return b;
        }
        if (raw != null) {
            problems.accept("config.yml: " + path + " '" + raw + "' is not true or false; using true");
        }
        return true;
    }

    /** {@code getInt} ignores a quoted number such as {@code '3308'}; this accepts it and reports anything that is not a whole number. */
    private static int intOrDefault(ConfigurationSection c, String path, int fallback, Consumer<String> problems) {
        Object raw = c.get(path);
        if (raw == null) {
            return fallback;
        }
        if (raw instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            problems.accept("config.yml: " + path + " '" + raw + "' is not a number; using " + fallback);
            return fallback;
        }
    }
}
