package dev.casperrs.cookieCore.storage;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Opens connections through the driver class directly instead of {@link java.sql.DriverManager}:
 * the drivers come from {@code CookieCoreLoader} on the plugin's class loader, which
 * DriverManager doesn't always see.
 */
public final class DriverConnectionFactory implements ConnectionFactory {

    private final DatabaseSettings settings;
    private Driver driver;

    public DriverConnectionFactory(DatabaseSettings settings) {
        this.settings = settings;
    }

    @Override
    public Connection open() throws SQLException {
        if (driver == null) {
            try {
                driver = (Driver) Class.forName(settings.driverClass(), true, DriverConnectionFactory.class.getClassLoader())
                        .getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException | ClassCastException e) {
                throw new SQLException("JDBC driver " + settings.driverClass() + " not found; did CookieCoreLoader load it?", e);
            }
        }
        Properties props = new Properties();
        if (settings.type() == DatabaseSettings.Type.MARIADB) {
            props.setProperty("user", settings.username());
            props.setProperty("password", settings.password());
        }
        Connection c = driver.connect(settings.jdbcUrl(), props);
        if (c == null) {
            throw new SQLException("Driver " + settings.driverClass() + " rejected the URL for " + settings.describe());
        }
        return c;
    }
}
