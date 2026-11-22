package com.capstone.data;

import com.capstone.model.PricePoint;

import java.util.List;

/**
 * Common interface for daily price-history providers.
 * Every fetcher must implement these three methods so
 * StockDataModule can treat them all identically in the chain.
 */
public interface StockFetcher {

    /** Human-readable provider name — used in log messages. */
    String providerName();

    /**
     * Returns true if this provider has a valid API key configured
     * in config.properties. StockDataModule skips unconfigured providers.
     */
    boolean isConfigured();

    /**
     * Fetches dated daily closing prices for the given ticker symbol,
     * in chronological order (oldest first).
     * Returns an empty list on any error — never throws.
     */
    List<PricePoint> fetchHistory(String symbol);
}
