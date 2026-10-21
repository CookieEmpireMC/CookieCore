package dev.casperrs.cookieCore.storage;

import java.sql.Connection;
import java.sql.SQLException;

/** Opens a new JDBC connection. Tests can pass {@code () -> DriverManager.getConnection("jdbc:h2:mem:…")}. */
@FunctionalInterface
public interface ConnectionFactory {
    Connection open() throws SQLException;
}
