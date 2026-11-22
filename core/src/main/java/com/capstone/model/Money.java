package com.capstone.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Exact money arithmetic: share prices to 4 decimal places, cash amounts to the cent. */
public final class Money {

    public static final int PRICE_SCALE = 4;
    public static final int CASH_SCALE  = 2;

    private Money() {}

    /** A share price from an API (double) as an exact 4-dp value. */
    public static BigDecimal price(double price) {
        return BigDecimal.valueOf(price).setScale(PRICE_SCALE, RoundingMode.HALF_UP);
    }

    /** price × quantity, rounded to the cent. */
    public static BigDecimal total(BigDecimal price, int quantity) {
        return price.multiply(BigDecimal.valueOf(quantity)).setScale(CASH_SCALE, RoundingMode.HALF_UP);
    }

    /** Weighted average cost after adding addQty shares at addPrice to an existing position. */
    public static BigDecimal averagePrice(BigDecimal oldAvg, int oldQty, BigDecimal addPrice, int addQty) {
        BigDecimal cost = oldAvg.multiply(BigDecimal.valueOf(oldQty)).add(addPrice.multiply(BigDecimal.valueOf(addQty)));
        return cost.divide(BigDecimal.valueOf(oldQty + addQty), PRICE_SCALE, RoundingMode.HALF_UP);
    }

    public static String format(BigDecimal amount) {
        return String.format("$%,.2f", amount);
    }
}
