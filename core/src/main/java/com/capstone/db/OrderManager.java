package com.capstone.db;

import com.capstone.model.Money;
import com.capstone.model.Order;
import com.capstone.model.TradeResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Places, cancels and executes limit and stop orders. All methods block on the DB —
 * call them off the Swing UI thread.
 *
 * Each fill is one transaction: lock the order row, confirm it's still OPEN, trade,
 * and mark it FILLED or REJECTED. So an order fills at most once, even when the
 * desktop and web apps check prices at the same moment.
 */
public class OrderManager {

    private static final Logger log = LoggerFactory.getLogger(OrderManager.class);

    public static final int MAX_OPEN_ORDERS = 50;
    public static final int MAX_QUANTITY = 1_000_000;

    /** An order that was filled or rejected by {@link #checkAndFill}. */
    public record Outcome(int orderId, int userId, String symbol, boolean filled, String message) {}

    private final PortfolioManager portfolio;

    public OrderManager(PortfolioManager portfolio) { this.portfolio = portfolio; }

    /** Places an OPEN order. Throws IllegalArgumentException with a user-facing reason if it isn't valid. */
    public Order place(int userId, String symbol, Order.Side side, Order.Type type, int quantity, double triggerPrice) throws SQLException {
        if (quantity < 1 || quantity > MAX_QUANTITY) throw new IllegalArgumentException("Quantity must be between 1 and " + MAX_QUANTITY);
        if (!(triggerPrice > 0) || triggerPrice > 1_000_000) throw new IllegalArgumentException("Trigger price must be between $0 and $1,000,000");
        try (Connection conn = DBConnection.get()) {
            conn.setAutoCommit(false);
            try {
                // Lock the user row so concurrent placements can't exceed the open-order limit
                try (PreparedStatement lock = conn.prepareStatement("SELECT id FROM users WHERE id = ? FOR UPDATE")) {
                    lock.setInt(1, userId); lock.executeQuery().close();
                }
                if (countOpen(conn, userId, null) >= MAX_OPEN_ORDERS) {
                    conn.rollback();
                    throw new IllegalArgumentException("You already have " + MAX_OPEN_ORDERS + " open orders. Cancel one first.");
                }
                int id;
                String sql = "INSERT INTO orders (user_id, symbol, side, order_type, quantity, trigger_price) VALUES (?, ?, ?, ?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, userId); ps.setString(2, symbol); ps.setString(3, side.name()); ps.setString(4, type.name());
                    ps.setInt(5, quantity); ps.setBigDecimal(6, Money.price(triggerPrice));
                    ps.executeUpdate();
                    try (ResultSet k = ps.getGeneratedKeys()) { k.next(); id = k.getInt(1); }
                }
                conn.commit();
                return find(id);
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    /** Cancels one of the user's OPEN orders. Returns false if it isn't theirs or is no longer open. */
    public boolean cancel(int userId, int orderId) throws SQLException {
        String sql = "UPDATE orders SET status = 'CANCELLED', closed_at = CURRENT_TIMESTAMP, message = 'Cancelled' "
                   + "WHERE id = ? AND user_id = ? AND status = 'OPEN'";
        try (Connection conn = DBConnection.get(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId); ps.setInt(2, userId);
            return ps.executeUpdate() == 1;
        }
    }

    /** The user's open orders (oldest first), then their 50 most recently closed. */
    public List<Order> list(int userId) throws SQLException {
        List<Order> orders = new ArrayList<>(query("SELECT * FROM orders WHERE user_id = ? AND status = 'OPEN' ORDER BY id", userId));
        orders.addAll(query("SELECT * FROM orders WHERE user_id = ? AND status <> 'OPEN' ORDER BY closed_at DESC, id DESC LIMIT 50", userId));
        return orders;
    }

    /** Open orders the user has for symbol (used to stop removing it from the watchlist). */
    public int countOpen(int userId, String symbol) throws SQLException {
        try (Connection conn = DBConnection.get()) { return countOpen(conn, userId, symbol); }
    }

    /**
     * Fills every OPEN order (any user) whose trigger is met by the given prices.
     * Refused trades (not enough cash or shares) are marked REJECTED with the reason.
     */
    public List<Outcome> checkAndFill(Map<String, Double> prices) {
        if (prices.isEmpty()) return List.of();
        List<Order> candidates = new ArrayList<>();
        String placeholders = String.join(",", Collections.nCopies(prices.size(), "?"));
        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM orders WHERE status = 'OPEN' AND symbol IN (" + placeholders + ") ORDER BY id")) {
            int i = 1;
            for (String symbol : prices.keySet()) ps.setString(i++, symbol);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) candidates.add(map(rs)); }
        } catch (SQLException e) {
            log.warn("Could not check open orders: {}", e.getMessage());
            return List.of();
        }

        List<Outcome> outcomes = new ArrayList<>();
        for (Order o : candidates) {
            double price = prices.get(o.symbol());
            if (!o.triggers(price)) continue;
            try {
                Outcome out = fill(o.id(), price);
                if (out != null) outcomes.add(out);
            } catch (SQLException e) {
                log.warn("Could not fill order {}: {}", o.id(), e.getMessage());   // stays OPEN, retried next time
            }
        }
        return outcomes;
    }

    /** Executes one triggered order in a single transaction; null if it was no longer OPEN. */
    private Outcome fill(int orderId, double price) throws SQLException {
        try (Connection conn = DBConnection.get()) {
            conn.setAutoCommit(false);
            try {
                Order o;
                try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM orders WHERE id = ? FOR UPDATE")) {
                    ps.setInt(1, orderId);
                    try (ResultSet rs = ps.executeQuery()) { if (!rs.next()) { conn.rollback(); return null; } o = map(rs); }
                }
                if (o.status() != Order.Status.OPEN || !o.triggers(price)) { conn.rollback(); return null; }

                TradeResult r = o.side() == Order.Side.BUY
                    ? portfolio.buy(conn, o.userId(), o.symbol(), o.quantity(), price)
                    : portfolio.sell(conn, o.userId(), o.symbol(), o.quantity(), price);
                String message = (r.success() ? "" : "Rejected: ") + r.message();
                if (message.length() > 300) message = message.substring(0, 300);
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE orders SET status = ?, closed_at = CURRENT_TIMESTAMP, fill_price = ?, message = ? WHERE id = ?")) {
                    ps.setString(1, r.success() ? "FILLED" : "REJECTED");
                    ps.setBigDecimal(2, r.success() ? Money.price(price) : null);
                    ps.setString(3, message);
                    ps.setInt(4, orderId);
                    ps.executeUpdate();
                }
                conn.commit();
                log.info("Order {} ({} {} {} x{} @ trigger {}) {} at {}", orderId, o.type(), o.side(), o.symbol(), o.quantity(),
                    o.triggerPrice(), r.success() ? "filled" : "rejected", price);
                return new Outcome(orderId, o.userId(), o.symbol(), r.success(), message);
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    public Order find(int orderId) throws SQLException {
        List<Order> found = query("SELECT * FROM orders WHERE id = ?", orderId);
        return found.isEmpty() ? null : found.get(0);
    }

    private static int countOpen(Connection conn, int userId, String symbol) throws SQLException {
        String sql = "SELECT COUNT(*) FROM orders WHERE user_id = ? AND status = 'OPEN'" + (symbol == null ? "" : " AND symbol = ?");
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            if (symbol != null) ps.setString(2, symbol);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getInt(1); }
        }
    }

    private static List<Order> query(String sql, int param) throws SQLException {
        List<Order> out = new ArrayList<>();
        try (Connection conn = DBConnection.get(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, param);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) out.add(map(rs)); }
        }
        return out;
    }

    private static Order map(ResultSet rs) throws SQLException {
        Timestamp closed = rs.getTimestamp("closed_at");
        return new Order(rs.getInt("id"), rs.getInt("user_id"), rs.getString("symbol"),
            Order.Side.valueOf(rs.getString("side")), Order.Type.valueOf(rs.getString("order_type")),
            rs.getInt("quantity"), rs.getBigDecimal("trigger_price"), Order.Status.valueOf(rs.getString("status")),
            rs.getTimestamp("created_at").toLocalDateTime(), closed == null ? null : closed.toLocalDateTime(),
            rs.getBigDecimal("fill_price"), rs.getString("message"));
    }
}
