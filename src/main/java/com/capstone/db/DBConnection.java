package com.capstone.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;

public class DBConnection {
    private static final HikariDataSource pool;

    static {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(AppConfig.getDbUrl());
        cfg.setUsername(AppConfig.getDbUser());
        cfg.setPassword(AppConfig.getDbPassword());
        cfg.setMaximumPoolSize(10);
        cfg.setMinimumIdle(2);
        cfg.setIdleTimeout(30_000);
        cfg.setConnectionTimeout(10_000);
        cfg.setMaxLifetime(1_800_000);
        cfg.setPoolName("StockAppPool");
        cfg.setConnectionTestQuery("SELECT 1");
        pool = new HikariDataSource(cfg);
    }

    public static Connection get() throws SQLException { return pool.getConnection(); }

    public static void shutdown() {
        if (pool != null && !pool.isClosed()) pool.close();
    }
}
