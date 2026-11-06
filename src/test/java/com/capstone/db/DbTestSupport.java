package com.capstone.db;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Base for tests that need a real MySQL. Set TEST_DB_URL (and optionally TEST_DB_USER /
 * TEST_DB_PASSWORD) to run them, e.g.
 *   TEST_DB_URL='jdbc:mysql://127.0.0.1:3306/stockdb_test?createDatabaseIfNotExist=true'
 * Without it they are skipped. CI runs them against a MySQL service container.
 * Every table is emptied before each test, so never point this at a real database.
 */
abstract class DbTestSupport {

    static final String URL      = System.getenv("TEST_DB_URL");
    static final String USER     = envOr("TEST_DB_USER", "root");
    static final String PASSWORD = envOr("TEST_DB_PASSWORD", "");

    @BeforeAll
    static void connect() {
        Assumptions.assumeTrue(URL != null && !URL.isBlank(), "TEST_DB_URL not set — skipping database tests");
        DBConnection.configure(URL, USER, PASSWORD);
    }

    @AfterAll
    static void disconnect() {
        DBConnection.shutdown();
    }

    @BeforeEach
    void cleanTables() throws SQLException {
        try (Connection c = DBConnection.get(); Statement s = c.createStatement()) {
            s.executeUpdate("DELETE FROM transactions");
            s.executeUpdate("DELETE FROM portfolio");
            s.executeUpdate("DELETE FROM users");
            s.executeUpdate("DELETE FROM stocks");
        }
    }

    static String scalar(String sql) throws SQLException {
        try (Connection c = DBConnection.get(); Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    static void exec(String sql) throws SQLException {
        try (Connection c = DBConnection.get(); Statement s = c.createStatement()) {
            s.executeUpdate(sql);
        }
    }

    private static String envOr(String name, String fallback) {
        String v = System.getenv(name);
        return v == null ? fallback : v;
    }
}
