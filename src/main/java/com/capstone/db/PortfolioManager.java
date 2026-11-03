package com.capstone.db;

import com.capstone.model.Money;
import com.capstone.model.PortfolioEntry;
import com.capstone.model.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.*;
import java.util.*;

/**
 * Buying, selling and reading a user's holdings. All methods block on the DB —
 * call them off the Swing UI thread.
 *
 * Trades lock the user's row first (SELECT … FOR UPDATE), then the portfolio row,
 * always in that order, so concurrent trades can't overspend or deadlock.
 */
public class PortfolioManager {

    private static final Logger log = LoggerFactory.getLogger(PortfolioManager.class);

    public String buyStock(int userId, String stockName, int quantity, double marketPrice) {
        if (quantity <= 0) return "Quantity must be greater than zero.";
        BigDecimal price     = Money.price(marketPrice);
        BigDecimal totalCost = Money.total(price, quantity);
        try (Connection conn = DBConnection.get()) {
            conn.setAutoCommit(false);
            try {
                BigDecimal balance = lockBalance(conn, userId);
                if (balance.compareTo(totalCost) < 0) {
                    conn.rollback();
                    return String.format("Insufficient balance. Need %s, have %s.", Money.format(totalCost), Money.format(balance));
                }
                BigDecimal newBalance = balance.subtract(totalCost);
                updateBalance(conn, userId, newBalance);
                int stockId = getOrCreateStockId(conn, stockName, price);
                addToPosition(conn, userId, stockId, quantity, price);
                logTransaction(conn, userId, stockName, quantity, price, totalCost, "BUY");
                conn.commit();
                return String.format("Bought %d of %s @ %s  |  Total: %s  |  Remaining balance: %s",
                    quantity, stockName, Money.format(price), Money.format(totalCost), Money.format(newBalance));
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            log.error("Buy failed for user {} ({} x {})", userId, quantity, stockName, e);
            return "Buy failed: " + e.getMessage();
        }
    }

    public String sellStock(int userId, String stockName, int quantity, double marketPrice) {
        if (quantity <= 0) return "Quantity must be greater than zero.";
        BigDecimal price      = Money.price(marketPrice);
        BigDecimal totalValue = Money.total(price, quantity);
        try (Connection conn = DBConnection.get()) {
            conn.setAutoCommit(false);
            try {
                BigDecimal balance = lockBalance(conn, userId);
                int stockId = findStockId(conn, stockName);
                int held = stockId < 0 ? 0 : lockHeldQuantity(conn, userId, stockId);
                if (held == 0) { conn.rollback(); return "You don't hold any shares of " + stockName + "."; }
                if (held < quantity) { conn.rollback(); return String.format("You only hold %d share(s) of %s.", held, stockName); }
                removeFromPosition(conn, userId, stockId, held - quantity);
                BigDecimal newBalance = balance.add(totalValue);
                updateBalance(conn, userId, newBalance);
                logTransaction(conn, userId, stockName, quantity, price, totalValue, "SELL");
                conn.commit();
                return String.format("Sold %d of %s @ %s  |  Received: %s  |  New balance: %s",
                    quantity, stockName, Money.format(price), Money.format(totalValue), Money.format(newBalance));
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            log.error("Sell failed for user {} ({} x {})", userId, quantity, stockName, e);
            return "Sell failed: " + e.getMessage();
        }
    }

    /** Holdings valued at livePrices (display name → price), falling back to the cached DB price. */
    public List<PortfolioEntry> viewPortfolioWithPnL(int userId, Map<String, Double> livePrices) throws SQLException {
        List<PortfolioEntry> entries = new ArrayList<>();
        String sql = "SELECT s.stock_name, s.current_price, p.quantity, p.avg_price FROM portfolio p JOIN stocks s ON p.stock_id = s.id WHERE p.user_id = ? ORDER BY s.stock_name";
        try (Connection conn = DBConnection.get(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("stock_name");
                    Double live = livePrices == null ? null : livePrices.get(name);
                    BigDecimal current = live != null ? Money.price(live) : rs.getBigDecimal("current_price");
                    entries.add(new PortfolioEntry(name, rs.getInt("quantity"), rs.getBigDecimal("avg_price"), current));
                }
            }
        }
        return entries;
    }

    /** Newest first. */
    public List<Transaction> viewTransactions(int userId) throws SQLException {
        List<Transaction> list = new ArrayList<>();
        String sql = "SELECT type, stock_name, quantity, price, total, ts FROM transactions WHERE user_id = ? ORDER BY ts DESC, id DESC";
        try (Connection conn = DBConnection.get(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Transaction(rs.getTimestamp("ts").toLocalDateTime(), rs.getString("type"),
                        rs.getString("stock_name"), rs.getInt("quantity"), rs.getBigDecimal("price"), rs.getBigDecimal("total")));
                }
            }
        }
        return list;
    }

    public BigDecimal getUserBalance(int userId) throws SQLException {
        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement("SELECT balance FROM users WHERE id = ?")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getBigDecimal("balance");
            }
        }
        throw new SQLException("User not found: " + userId);
    }

    // ----------------------------------------------------------------
    // Helpers — all run inside the caller's transaction
    // ----------------------------------------------------------------

    private BigDecimal lockBalance(Connection conn, int userId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT balance FROM users WHERE id = ? FOR UPDATE")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getBigDecimal("balance");
            }
        }
        throw new SQLException("User not found: " + userId);
    }

    private void updateBalance(Connection conn, int userId, BigDecimal balance) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("UPDATE users SET balance = ? WHERE id = ?")) {
            ps.setBigDecimal(1, balance); ps.setInt(2, userId); ps.executeUpdate();
        }
    }

    private int lockHeldQuantity(Connection conn, int userId, int stockId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT quantity FROM portfolio WHERE user_id = ? AND stock_id = ? FOR UPDATE")) {
            ps.setInt(1, userId); ps.setInt(2, stockId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("quantity") : 0;
            }
        }
    }

    private void addToPosition(Connection conn, int userId, int stockId, int qty, BigDecimal price) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT quantity, avg_price FROM portfolio WHERE user_id = ? AND stock_id = ? FOR UPDATE")) {
            ps.setInt(1, userId); ps.setInt(2, stockId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int eq = rs.getInt("quantity");
                    BigDecimal newAvg = Money.averagePrice(rs.getBigDecimal("avg_price"), eq, price, qty);
                    try (PreparedStatement u = conn.prepareStatement("UPDATE portfolio SET quantity=?, avg_price=? WHERE user_id=? AND stock_id=?")) {
                        u.setInt(1, eq + qty); u.setBigDecimal(2, newAvg); u.setInt(3, userId); u.setInt(4, stockId); u.executeUpdate();
                    }
                    return;
                }
            }
        }
        try (PreparedStatement i = conn.prepareStatement("INSERT INTO portfolio (user_id,stock_id,quantity,avg_price) VALUES(?,?,?,?)")) {
            i.setInt(1, userId); i.setInt(2, stockId); i.setInt(3, qty); i.setBigDecimal(4, price); i.executeUpdate();
        }
    }

    private void removeFromPosition(Connection conn, int userId, int stockId, int remaining) throws SQLException {
        if (remaining == 0) {
            try (PreparedStatement d = conn.prepareStatement("DELETE FROM portfolio WHERE user_id=? AND stock_id=?")) {
                d.setInt(1, userId); d.setInt(2, stockId); d.executeUpdate();
            }
        } else {
            try (PreparedStatement u = conn.prepareStatement("UPDATE portfolio SET quantity=? WHERE user_id=? AND stock_id=?")) {
                u.setInt(1, remaining); u.setInt(2, userId); u.setInt(3, stockId); u.executeUpdate();
            }
        }
    }

    private void logTransaction(Connection conn, int userId, String stockName, int qty, BigDecimal price, BigDecimal total, String type) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO transactions (user_id,stock_name,quantity,price,total,type) VALUES(?,?,?,?,?,?)")) {
            ps.setInt(1, userId); ps.setString(2, stockName); ps.setInt(3, qty); ps.setBigDecimal(4, price); ps.setBigDecimal(5, total); ps.setString(6, type); ps.executeUpdate();
        }
    }

    /** Atomic get-or-insert: LAST_INSERT_ID(id) makes the existing row's id come back as the generated key. */
    private int getOrCreateStockId(Connection conn, String name, BigDecimal price) throws SQLException {
        String sql = "INSERT INTO stocks (stock_name,current_price) VALUES(?,?) ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name); ps.setBigDecimal(2, price); ps.executeUpdate();
            try (ResultSet k = ps.getGeneratedKeys()) {
                if (k.next()) return k.getInt(1);
            }
        }
        throw new SQLException("Could not create stock: " + name);
    }

    private int findStockId(Connection conn, String name) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM stocks WHERE stock_name=?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("id") : -1;
            }
        }
    }
}
