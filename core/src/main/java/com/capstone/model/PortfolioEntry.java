package com.capstone.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class PortfolioEntry {
    public final String     stockName;
    public final int        quantity;
    public final BigDecimal avgPrice;
    public final BigDecimal currentPrice;
    public final BigDecimal marketValue;
    public final BigDecimal unrealizedPnL;
    public final double     pnlPercent;

    public PortfolioEntry(String stockName, int quantity, BigDecimal avgPrice, BigDecimal currentPrice) {
        this.stockName    = stockName;
        this.quantity     = quantity;
        this.avgPrice     = avgPrice;
        this.currentPrice = currentPrice;
        this.marketValue  = Money.total(currentPrice, quantity);
        BigDecimal costBasis = Money.total(avgPrice, quantity);
        this.unrealizedPnL = marketValue.subtract(costBasis);
        this.pnlPercent    = costBasis.signum() == 0 ? 0
            : unrealizedPnL.multiply(BigDecimal.valueOf(100)).divide(costBasis, 4, RoundingMode.HALF_UP).doubleValue();
    }
}
