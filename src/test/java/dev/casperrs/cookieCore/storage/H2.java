package dev.casperrs.cookieCore.storage;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

/** A fresh in-memory H2 database per test. */
final class H2 {

    final String url = "jdbc:h2:mem:t" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1";
    int opened;

    ConnectionFactory factory() {
        return () -> {
            opened++;
            return DriverManager.getConnection(url);
        };
    }

    Connection raw() throws SQLException {
        return DriverManager.getConnection(url);
    }

    int count(String table) throws SQLException {
        try (Connection c = raw(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    boolean tableExists(String table) throws SQLException {
        try (Connection c = raw(); ResultSet rs = c.getMetaData().getTables(null, null, table.toUpperCase(), null)) {
            return rs.next();
        }
    }

    void execute(String sql) throws SQLException {
        try (Connection c = raw(); Statement st = c.createStatement()) {
            st.execute(sql);
        }
    }
}
