package com.capstone.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Shared connection pool. Created on first use from config.properties / environment
 * variables; the schema is brought up to date with Flyway (src/main/resources/db/migration)
 * before the first connection is handed out.
 */
public final class DBConnection {

    private static final Logger log = LoggerFactory.getLogger(DBConnection.class);

    private static HikariDataSource pool;
    private static boolean migrated;

    private DBConnection() {}

    public static Connection get() throws SQLException {
        return ready().getConnection();
    }

    /** Points the pool at a different database (used by tests). Closes any existing pool. */
    public static synchronized void configure(String url, String user, String password) {
        shutdown();
        pool = createPool(url, user, password);
    }

    public static synchronized void shutdown() {
        if (pool != null && !pool.isClosed()) pool.close();
        pool = null;
        migrated = false;
    }

    private static synchronized HikariDataSource ready() throws SQLException {
        if (pool == null) pool = createPool(AppConfig.getDbUrl(), AppConfig.getDbUser(), AppConfig.getDbPassword());
        if (!migrated) {
            // Retried on the next call if MySQL is down
            try {
                var result = Flyway.configure()
                    .dataSource(pool)
                    // Databases created from the old stockdb.sql have tables but no
                    // Flyway history: treat them as V1 and apply later migrations
                    .baselineOnMigrate(true)
                    .baselineVersion("1")
                    .load()
                    .migrate();
                if (result.migrationsExecuted > 0) {
                    log.info("Applied {} database migration(s); schema now at version {}", result.migrationsExecuted, result.targetSchemaVersion);
                }
                migrated = true;
            } catch (FlywayException e) {
                throw new SQLException("Database migration failed: " + e.getMessage(), e);
            }
        }
        return pool;
    }

    private static HikariDataSource createPool(String url, String user, String password) {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(url);
        cfg.setUsername(user);
        cfg.setPassword(password);
        cfg.setMaximumPoolSize(10);
        cfg.setMinimumIdle(2);
        cfg.setIdleTimeout(30_000);
        cfg.setConnectionTimeout(10_000);
        cfg.setMaxLifetime(1_800_000);
        cfg.setPoolName("StockAppPool");
        cfg.setConnectionTestQuery("SELECT 1");
        // Don't fail at startup if MySQL is down — get() throws SQLException
        // instead, and works once MySQL is back
        cfg.setInitializationFailTimeout(-1);
        return new HikariDataSource(cfg);
    }
}
