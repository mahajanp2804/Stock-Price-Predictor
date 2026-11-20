package com.capstone.db;

import com.capstone.model.PricePoint;

import java.math.BigDecimal;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** Stores daily price history and the latest price per stock in MySQL. All methods block on the DB. */
public class PriceCache {

    /** Most recent days returned by loadHistory. */
    static final int MAX_DAYS = 400;

    /** Replaces/extends a stock's cached history and records when it was fetched. */
    public void saveHistory(String stockName, List<PricePoint> history) throws SQLException {
        String upsert = "INSERT INTO price_history (stock_name, trade_date, close) VALUES (?, ?, ?) "
                      + "ON DUPLICATE KEY UPDATE close = VALUES(close)";
        String logFetch = "INSERT INTO history_fetch_log (stock_name, fetched_at) VALUES (?, ?) "
                        + "ON DUPLICATE KEY UPDATE fetched_at = VALUES(fetched_at)";
        try (Connection conn = DBConnection.get()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(upsert); PreparedStatement log = conn.prepareStatement(logFetch)) {
                for (PricePoint p : history) {
                    ps.setString(1, stockName);
                    ps.setDate(2, Date.valueOf(p.date()));
                    ps.setBigDecimal(3, BigDecimal.valueOf(p.close()));
                    ps.addBatch();
                }
                ps.executeBatch();
                log.setString(1, stockName);
                log.setTimestamp(2, Timestamp.from(Instant.now()));
                log.executeUpdate();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    /** The most recent MAX_DAYS cached closes, oldest first; empty if none. */
    public List<PricePoint> loadHistory(String stockName) throws SQLException {
        List<PricePoint> history = new ArrayList<>();
        String sql = "SELECT trade_date, close FROM price_history WHERE stock_name = ? ORDER BY trade_date DESC LIMIT ?";
        try (Connection conn = DBConnection.get(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, stockName);
            ps.setInt(2, MAX_DAYS);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) history.add(new PricePoint(rs.getDate("trade_date").toLocalDate(), rs.getBigDecimal("close").doubleValue()));
            }
        }
        Collections.reverse(history);
        return history;
    }

    /** When the stock's history was last saved from a provider. */
    public Optional<Instant> lastFetched(String stockName) throws SQLException {
        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement("SELECT fetched_at FROM history_fetch_log WHERE stock_name = ?")) {
            ps.setString(1, stockName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getTimestamp("fetched_at").toInstant()) : Optional.empty();
            }
        }
    }

    /** Records the latest price (used for portfolio valuation and as a last-resort fallback). */
    public void savePrice(String stockName, double price) throws SQLException {
        String sql = "INSERT INTO stocks (stock_name, current_price) VALUES (?, ?) "
                   + "ON DUPLICATE KEY UPDATE current_price = VALUES(current_price)";
        try (Connection conn = DBConnection.get(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, stockName);
            ps.setBigDecimal(2, BigDecimal.valueOf(price));
            ps.executeUpdate();
        }
    }

    /** The last recorded price, if any. */
    public Optional<Double> loadPrice(String stockName) throws SQLException {
        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement("SELECT current_price FROM stocks WHERE stock_name = ?")) {
            ps.setString(1, stockName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getBigDecimal("current_price").doubleValue()) : Optional.empty();
            }
        }
    }
}
