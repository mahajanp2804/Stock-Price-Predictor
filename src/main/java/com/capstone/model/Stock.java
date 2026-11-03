package com.capstone.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A stock's daily closing-price history plus, when available, a newer live quote.
 * The history feeds the prediction module; the live quote is what trades execute at.
 */
public class Stock {
    private final String name;
    private final List<Double> priceHistory;
    private volatile double  livePrice;      // 0 = no live quote yet
    private volatile Instant liveUpdatedAt;

    public Stock(String name, List<Double> priceHistory) {
        if (priceHistory == null || priceHistory.isEmpty()) throw new IllegalArgumentException("priceHistory must not be empty");
        this.name = name;
        this.priceHistory = new ArrayList<>(priceHistory);
    }

    public String getName() { return name; }

    /** Daily closes, oldest first (a copy). */
    public List<Double> getPriceHistory() { return new ArrayList<>(priceHistory); }

    /** Live quote if one has been fetched, otherwise the last daily close. */
    public double getLatestPrice() { return livePrice > 0 ? livePrice : priceHistory.get(priceHistory.size() - 1); }

    public void setLivePrice(double price) {
        if (price <= 0) return;
        this.livePrice = price;
        this.liveUpdatedAt = Instant.now();
    }

    /** When the live quote was last updated, or null if the price is still the last daily close. */
    public Instant getLiveUpdatedAt() { return liveUpdatedAt; }
}
