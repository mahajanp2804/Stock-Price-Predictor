package com.capstone.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A limit or stop order.
 *
 * <ul>
 *   <li>LIMIT BUY  — fills when the price is at or below the trigger (buy the dip)</li>
 *   <li>LIMIT SELL — fills when the price is at or above the trigger (take profit)</li>
 *   <li>STOP BUY   — fills when the price rises to the trigger (breakout)</li>
 *   <li>STOP SELL  — fills when the price falls to the trigger (stop-loss)</li>
 * </ul>
 * A triggered order executes at the market price that triggered it.
 */
public record Order(int id, int userId, String symbol, Side side, Type type, int quantity, BigDecimal triggerPrice,
                    Status status, LocalDateTime createdAt, LocalDateTime closedAt, BigDecimal fillPrice, String message) {

    public enum Side { BUY, SELL }
    public enum Type { LIMIT, STOP }
    public enum Status { OPEN, FILLED, REJECTED, CANCELLED }

    /** True if an order of this side and type should execute at the given market price. */
    public static boolean triggers(Side side, Type type, BigDecimal trigger, double marketPrice) {
        int cmp = Money.price(marketPrice).compareTo(trigger);
        // Limit buys and stop sells wait for the price to fall; limit sells and stop buys for it to rise
        boolean waitsForFall = (side == Side.BUY) == (type == Type.LIMIT);
        return waitsForFall ? cmp <= 0 : cmp >= 0;
    }

    public boolean triggers(double marketPrice) {
        return triggers(side, type, triggerPrice, marketPrice);
    }
}
