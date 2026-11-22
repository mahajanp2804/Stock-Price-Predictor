package com.capstone.model;

import java.math.BigDecimal;

/**
 * Outcome of a buy or sell.
 *
 * @param success    true if the trade went through
 * @param message    human-readable summary or reason it was refused
 * @param newBalance cash balance after the trade; null if it didn't go through
 */
public record TradeResult(boolean success, String message, BigDecimal newBalance) {

    public static TradeResult ok(String message, BigDecimal newBalance) { return new TradeResult(true, message, newBalance); }

    public static TradeResult refused(String message) { return new TradeResult(false, message, null); }
}
