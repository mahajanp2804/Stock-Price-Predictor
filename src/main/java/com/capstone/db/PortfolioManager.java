package com.capstone.db;

import com.capstone.model.PortfolioEntry;
import java.sql.*;
import java.util.*;

public class PortfolioManager {

    public String buyStock(int userId, String stockName, int quantity, double price) {
        if (quantity <= 0) return "Quantity must be greater than zero.";
        double totalCost = price * quantity;
        try (Connection conn = DBConnection.get()) {
            conn.setAutoCommit(false);
            double balance = getBalance(conn, userId);
            if (balance < totalCost) { conn.rollback(); return String.format("Insufficient balance. Need $%.2f, have $%.2f.", totalCost, balance); }
            updateBalance(conn, userId, balance - totalCost);
            int stockId = getOrCreateStockId(conn, stockName, price);
            upsertPortfolio(conn, userId, stockId, quantity, price, true);
            logTransaction(conn, userId, stockName, quantity, price, totalCost, "BUY");
            conn.commit();
            return String.format("Bought %d of %s @ $%.2f  |  Total: $%.2f  |  Remaining balance: $%.2f", quantity, stockName, price, totalCost, balance - totalCost);
        } catch (SQLException e) { e.printStackTrace(); return "Buy failed: " + e.getMessage(); }
    }

    public String sellStock(int userId, String stockName, int quantity, double price) {
        if (quantity <= 0) return "Quantity must be greater than zero.";
        double totalValue = price * quantity;
        try (Connection conn = DBConnection.get()) {
            conn.setAutoCommit(false);
            int stockId = findStockId(conn, stockName);
            if (stockId < 0) { conn.rollback(); return "You don't hold any shares of " + stockName + "."; }
            int held = getHeldQuantity(conn, userId, stockId);
            if (held < quantity) { conn.rollback(); return String.format("You only hold %d share(s) of %s.", held, stockName); }
            upsertPortfolio(conn, userId, stockId, quantity, price, false);
            double balance = getBalance(conn, userId);
            updateBalance(conn, userId, balance + totalValue);
            logTransaction(conn, userId, stockName, quantity, price, totalValue, "SELL");
            conn.commit();
            return String.format("Sold %d of %s @ $%.2f  |  Received: $%.2f  |  New balance: $%.2f", quantity, stockName, price, totalValue, balance + totalValue);
        } catch (SQLException e) { e.printStackTrace(); return "Sell failed: " + e.getMessage(); }
    }

    public List<PortfolioEntry> viewPortfolioWithPnL(int userId, Map<String, Double> livePrices) {
        List<PortfolioEntry> entries = new ArrayList<>();
        String sql = "SELECT s.stock_name, s.current_price, p.quantity, p.avg_price FROM portfolio p JOIN stocks s ON p.stock_id = s.id WHERE p.user_id = ?";
        try (Connection conn = DBConnection.get(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId); ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String name = rs.getString("stock_name"); int qty = rs.getInt("quantity");
                double avg = rs.getDouble("avg_price"); double dbPrice = rs.getDouble("current_price");
                double current = (livePrices != null && livePrices.containsKey(name)) ? livePrices.get(name) : dbPrice;
                entries.add(new PortfolioEntry(name, qty, avg, current));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return entries;
    }

    public List<String> viewTransactions(int userId) {
        List<String> list = new ArrayList<>();
        String sql = "SELECT type, stock_name, quantity, price, total, ts FROM transactions WHERE user_id = ? ORDER BY ts DESC";
        try (Connection conn = DBConnection.get(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId); ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(String.format("[%s] %-4s  %-10s  x%d @ $%.2f  =  $%.2f",
                    rs.getTimestamp("ts").toString().substring(0, 16), rs.getString("type"),
                    rs.getString("stock_name"), rs.getInt("quantity"), rs.getDouble("price"), rs.getDouble("total")));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    public double getUserBalance(int userId) {
        try (Connection conn = DBConnection.get()) { return getBalance(conn, userId); }
        catch (SQLException e) { e.printStackTrace(); return 0; }
    }

    private double getBalance(Connection conn, int userId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT balance FROM users WHERE id = ?")) {
            ps.setInt(1, userId); ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getDouble("balance");
        }
        throw new SQLException("User not found: " + userId);
    }
    private void updateBalance(Connection conn, int userId, double b) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("UPDATE users SET balance = ? WHERE id = ?")) { ps.setDouble(1, b); ps.setInt(2, userId); ps.executeUpdate(); }
    }
    private int getHeldQuantity(Connection conn, int userId, int stockId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT quantity FROM portfolio WHERE user_id = ? AND stock_id = ?")) {
            ps.setInt(1, userId); ps.setInt(2, stockId); ResultSet rs = ps.executeQuery(); if (rs.next()) return rs.getInt("quantity");
        }
        return 0;
    }
    private void upsertPortfolio(Connection conn, int userId, int stockId, int qty, double price, boolean isBuy) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT quantity, avg_price FROM portfolio WHERE user_id = ? AND stock_id = ?")) {
            ps.setInt(1, userId); ps.setInt(2, stockId); ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                int eq = rs.getInt("quantity"); double ea = rs.getDouble("avg_price");
                if (isBuy) {
                    int nq = eq + qty; double na = ((ea * eq) + (price * qty)) / nq;
                    try (PreparedStatement u = conn.prepareStatement("UPDATE portfolio SET quantity=?, avg_price=? WHERE user_id=? AND stock_id=?")) { u.setInt(1, nq); u.setDouble(2, na); u.setInt(3, userId); u.setInt(4, stockId); u.executeUpdate(); }
                } else {
                    int nq = eq - qty;
                    if (nq == 0) { try (PreparedStatement d = conn.prepareStatement("DELETE FROM portfolio WHERE user_id=? AND stock_id=?")) { d.setInt(1, userId); d.setInt(2, stockId); d.executeUpdate(); } }
                    else { try (PreparedStatement u = conn.prepareStatement("UPDATE portfolio SET quantity=? WHERE user_id=? AND stock_id=?")) { u.setInt(1, nq); u.setInt(2, userId); u.setInt(3, stockId); u.executeUpdate(); } }
                }
            } else if (isBuy) {
                try (PreparedStatement i = conn.prepareStatement("INSERT INTO portfolio (user_id,stock_id,quantity,avg_price) VALUES(?,?,?,?)")) { i.setInt(1, userId); i.setInt(2, stockId); i.setInt(3, qty); i.setDouble(4, price); i.executeUpdate(); }
            }
        }
    }
    private void logTransaction(Connection conn, int userId, String stockName, int qty, double price, double total, String type) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO transactions (user_id,stock_name,quantity,price,total,type) VALUES(?,?,?,?,?,?)")) {
            ps.setInt(1, userId); ps.setString(2, stockName); ps.setInt(3, qty); ps.setDouble(4, price); ps.setDouble(5, total); ps.setString(6, type); ps.executeUpdate();
        }
    }
    private int getOrCreateStockId(Connection conn, String name, double price) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM stocks WHERE stock_name=?")) { ps.setString(1, name); ResultSet rs = ps.executeQuery(); if (rs.next()) return rs.getInt("id"); }
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO stocks (stock_name,current_price) VALUES(?,?)", Statement.RETURN_GENERATED_KEYS)) { ps.setString(1, name); ps.setDouble(2, price); ps.executeUpdate(); ResultSet k = ps.getGeneratedKeys(); if (k.next()) return k.getInt(1); }
        throw new SQLException("Could not create stock: " + name);
    }
    private int findStockId(Connection conn, String name) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM stocks WHERE stock_name=?")) { ps.setString(1, name); ResultSet rs = ps.executeQuery(); if (rs.next()) return rs.getInt("id"); }
        return -1;
    }
}
