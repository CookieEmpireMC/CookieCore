package dev.casperrs.cookieCore.storage;

import java.sql.Connection;
import java.sql.SQLException;

/** One lazily opened JDBC connection that is reopened when it goes stale. Single-threaded by contract, like its stores. */
final class JdbcSession {

    private final ConnectionFactory connections;
    private Connection connection;

    JdbcSession(ConnectionFactory connections) {
        this.connections = connections;
    }

    Connection connection() throws SQLException {
        if (connection == null || !connection.isValid(2)) {
            invalidate();
            connection = connections.open();
        }
        return connection;
    }

    void invalidate() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
                // already broken
            }
            connection = null;
        }
    }

    static String limit(String text, int max) {
        return text.length() > max ? text.substring(0, max) : text;
    }
}
