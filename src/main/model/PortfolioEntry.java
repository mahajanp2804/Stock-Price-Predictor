package com.capstone.model;

public class PortfolioEntry {
    public final String stockName;
    public final int    quantity;
    public final double avgPrice;
    public final double currentPrice;
    public final double marketValue;
    public final double unrealizedPnL;
    public final double pnlPercent;

    public PortfolioEntry(String stockName, int quantity, double avgPrice, double currentPrice) {
        this.stockName    = stockName;
        this.quantity     = quantity;
        this.avgPrice     = avgPrice;
        this.currentPrice = currentPrice;
        this.marketValue  = quantity * currentPrice;
        double costBasis  = quantity * avgPrice;
        this.unrealizedPnL = marketValue - costBasis;
        this.pnlPercent    = costBasis == 0 ? 0 : (unrealizedPnL / costBasis) * 100;
    }
}
