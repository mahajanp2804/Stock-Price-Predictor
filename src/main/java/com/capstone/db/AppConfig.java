package com.capstone.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Properties;

public class AppConfig {
    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);
    private static final Properties props = new Properties();

    static {
        try (InputStream in = AppConfig.class.getResourceAsStream("/config.properties")) {
            if (in == null) {
                try (InputStream fi = new java.io.FileInputStream("config.properties")) { props.load(fi); }
            } else {
                props.load(in);
            }
        } catch (Exception e) {
            log.warn("Could not load config.properties ({}); using environment variables only", e.getMessage());
        }
    }

    // Environment variable (e.g. finnhub.key -> FINNHUB_KEY) wins over config.properties
    public static String get(String key) {
        String env = System.getenv(key.toUpperCase().replace('.', '_'));
        if (env != null && !env.isBlank()) return env;
        return props.getProperty(key, "");
    }

    // Individual API keys — returns empty string if not set
    public static String getFinnhubKey()           { return get("finnhub.key"); }
    public static String getTwelvedataKey()        { return get("twelvedata.key"); }
    public static String getAlphavantageKey()      { return get("alphavantage.key"); }

    // DB credentials
    public static String getDbUrl()                { return get("db.url"); }
    public static String getDbUser()               { return get("db.user"); }
    public static String getDbPassword()           { return get("db.password"); }
}
