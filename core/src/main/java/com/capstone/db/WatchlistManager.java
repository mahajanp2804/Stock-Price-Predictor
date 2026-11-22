package com.capstone.db;

import com.capstone.model.WatchItem;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** Each user's watchlist. All methods block on the DB — call them off the Swing UI thread. */
public class WatchlistManager {

    /** Keeps startup and the per-minute quote refresh within the free API limits. */
    public static final int MAX_STOCKS = 20;

    public enum AddResult { ADDED, ALREADY_THERE, FULL }

    /** The user's stocks, sorted by symbol. */
    public List<WatchItem> list(int userId) throws SQLException {
        List<WatchItem> items = new ArrayList<>();
        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement("SELECT symbol, company_name FROM watchlist WHERE user_id = ? ORDER BY symbol")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) items.add(new WatchItem(rs.getString("symbol"), rs.getString("company_name")));
            }
        }
        return items;
    }

    public AddResult add(int userId, String symbol, String companyName) throws SQLException {
        try (Connection conn = DBConnection.get()) {
            conn.setAutoCommit(false);
            try {
                // Lock the user's row so two adds can't both squeeze past the limit
                try (PreparedStatement lock = conn.prepareStatement("SELECT id FROM users WHERE id = ? FOR UPDATE")) {
                    lock.setInt(1, userId); lock.executeQuery().close();
                }
                int count;
                try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM watchlist WHERE user_id = ?")) {
                    ps.setInt(1, userId);
                    try (ResultSet rs = ps.executeQuery()) { rs.next(); count = rs.getInt(1); }
                }
                if (count >= MAX_STOCKS) { conn.rollback(); return AddResult.FULL; }
                int inserted;
                try (PreparedStatement ps = conn.prepareStatement("INSERT IGNORE INTO watchlist (user_id, symbol, company_name) VALUES (?, ?, ?)")) {
                    ps.setInt(1, userId); ps.setString(2, symbol); ps.setString(3, companyName);
                    inserted = ps.executeUpdate();
                }
                conn.commit();
                return inserted == 1 ? AddResult.ADDED : AddResult.ALREADY_THERE;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    public void remove(int userId, String symbol) throws SQLException {
        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM watchlist WHERE user_id = ? AND symbol = ?")) {
            ps.setInt(1, userId); ps.setString(2, symbol);
            ps.executeUpdate();
        }
    }

    /** Gives a new user the default stocks, inside the caller's transaction. */
    static void addDefaults(Connection conn, int userId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("INSERT IGNORE INTO watchlist (user_id, symbol, company_name) VALUES (?, ?, ?)")) {
            for (WatchItem d : WatchItem.DEFAULTS) {
                ps.setInt(1, userId); ps.setString(2, d.symbol()); ps.setString(3, d.companyName());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }
}
