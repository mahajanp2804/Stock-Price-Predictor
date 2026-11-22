package com.capstone.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

/**
 * A stock's dated daily closing prices plus, when available, a newer live quote.
 * The history feeds the prediction module; the live quote is what trades execute at.
 */
public class Stock {
    private final String symbol;
    private final String name;
    private final List<PricePoint> history;
    private volatile double  livePrice;      // 0 = no live quote yet
    private volatile Instant liveUpdatedAt;

    /**
     * @param symbol  ticker, e.g. "AAPL" — how the stock is identified everywhere
     * @param name    company name for display, e.g. "Apple Inc"
     */
    public Stock(String symbol, String name, List<PricePoint> history) {
        if (history == null || history.isEmpty()) throw new IllegalArgumentException("history must not be empty");
        this.symbol = symbol;
        this.name = name;
        this.history = List.copyOf(history);
    }

    public String getSymbol() { return symbol; }

    /** Company name for display. */
    public String getName() { return name; }

    /** Dated daily closes, oldest first. */
    public List<PricePoint> getHistory() { return history; }

    /** Daily closes only, oldest first (a copy). */
    public List<Double> getPriceHistory() {
        List<Double> closes = new ArrayList<>(history.size());
        for (PricePoint p : history) closes.add(p.close());
        return closes;
    }

    /** Live quote if one has been fetched, otherwise the last daily close. */
    public double getLatestPrice() { return livePrice > 0 ? livePrice : history.get(history.size() - 1).close(); }

    /**
     * Percent change from the first close on or after {@code since} to the latest price.
     * Empty if there's no close that old to compare against (e.g. only a cached price).
     */
    public OptionalDouble percentChangeSince(LocalDate since) {
        if (history.size() < 2 || history.get(0).date().isAfter(since)) return OptionalDouble.empty();
        for (PricePoint p : history) {
            if (!p.date().isBefore(since)) {
                return p.close() == 0 ? OptionalDouble.empty()
                    : OptionalDouble.of((getLatestPrice() - p.close()) / p.close() * 100);
            }
        }
        return OptionalDouble.empty();
    }

    public void setLivePrice(double price) {
        if (price <= 0) return;
        this.livePrice = price;
        this.liveUpdatedAt = Instant.now();
    }

    /** When the live quote was last updated, or null if the price is still the last daily close. */
    public Instant getLiveUpdatedAt() { return liveUpdatedAt; }
}
