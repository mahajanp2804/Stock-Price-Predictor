package com.capstone.db;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class MigrationDbTest extends DbTestSupport {

    @Test
    @DisplayName("a fresh database is migrated to the latest schema with DECIMAL money")
    void freshSchema() throws Exception {
        assertEquals("2", scalar("SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE success = 1"));
        assertEquals("decimal(15,2)", columnType("users", "balance"));
        assertEquals("decimal(15,4)", columnType("portfolio", "avg_price"));
    }

    @Test
    @DisplayName("a database made by the old stockdb.sql is baselined and upgraded, keeping its data")
    void legacyDatabaseUpgraded() throws Exception {
        String legacyUrl = URL.replaceFirst("(jdbc:mysql://[^/]+/)([^?]+)", "$1$2_legacy");
        assertNotEquals(URL, legacyUrl);
        if (!legacyUrl.contains("createDatabaseIfNotExist")) legacyUrl += (legacyUrl.contains("?") ? "&" : "?") + "createDatabaseIfNotExist=true";

        // Build the pre-Flyway schema by hand (DOUBLE money, no history table) with some data
        try (Connection c = DriverManager.getConnection(legacyUrl, USER, PASSWORD); Statement s = c.createStatement()) {
            for (String t : new String[]{"flyway_schema_history", "transactions", "portfolio", "stocks", "users"}) s.executeUpdate("DROP TABLE IF EXISTS " + t);
            s.executeUpdate("CREATE TABLE users (id INT PRIMARY KEY AUTO_INCREMENT, username VARCHAR(100) NOT NULL UNIQUE, password VARCHAR(60) NOT NULL, balance DOUBLE NOT NULL DEFAULT 10000)");
            s.executeUpdate("CREATE TABLE stocks (id INT PRIMARY KEY AUTO_INCREMENT, stock_name VARCHAR(100) NOT NULL UNIQUE, current_price DOUBLE NOT NULL)");
            s.executeUpdate("CREATE TABLE portfolio (id INT PRIMARY KEY AUTO_INCREMENT, user_id INT NOT NULL, stock_id INT NOT NULL, quantity INT NOT NULL, avg_price DOUBLE NOT NULL, FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE, FOREIGN KEY (stock_id) REFERENCES stocks(id) ON DELETE CASCADE, UNIQUE (user_id, stock_id))");
            s.executeUpdate("CREATE TABLE transactions (id INT PRIMARY KEY AUTO_INCREMENT, user_id INT NOT NULL, stock_name VARCHAR(100) NOT NULL, quantity INT NOT NULL, price DOUBLE NOT NULL, total DOUBLE NOT NULL, type ENUM('BUY','SELL') NOT NULL, ts TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE)");
            s.executeUpdate("INSERT INTO users(username,password,balance) VALUES('old','x',6616.8)");
            s.executeUpdate("INSERT INTO stocks(stock_name,current_price) VALUES('Apple',338.32)");
            s.executeUpdate("INSERT INTO portfolio(user_id,stock_id,quantity,avg_price) VALUES(1,1,10,338.32)");
        }

        DBConnection.configure(legacyUrl, USER, PASSWORD);
        assertEquals("6616.80", scalar("SELECT balance FROM users WHERE username='old'"));
        assertEquals("338.3200", scalar("SELECT avg_price FROM portfolio"));
        assertEquals("decimal(15,2)", columnType("users", "balance"));
        assertEquals("BASELINE", scalar("SELECT type FROM flyway_schema_history WHERE version = '1'"));
        assertEquals("1", scalar("SELECT success FROM flyway_schema_history WHERE version = '2'"));
    }

    @AfterEach
    void backToTestDatabase() {
        DBConnection.configure(URL, USER, PASSWORD);
    }

    private static String columnType(String table, String column) throws Exception {
        return scalar("SELECT COLUMN_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '" + table + "' AND COLUMN_NAME = '" + column + "'");
    }
}
