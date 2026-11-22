package com.capstone.data;

import com.capstone.db.PriceCache;
import com.capstone.model.PricePoint;
import com.capstone.model.Stock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads stock prices.
 *
 * Daily history (for prediction):
 *   0. DB history cache, if downloaded in the last CACHE_TTL
 *   1. Twelve Data   (800 req/day, 8 req/min)
 *   2. Alpha Vantage (25 req/day)
 *   3. DB history cache of any age
 *   4. Last recorded price only
 *
 * Live price (what trades execute at): Finnhub /quote (60 req/min).
 *
 * Only providers with a non-empty key in config.properties are used.
 * All methods here block on network/DB — call them off the Swing UI thread.
 */
public class StockDataModule {

    private static final Logger log = LoggerFactory.getLogger(StockDataModule.class);

    private final Map<String, Stock> stockMap = new ConcurrentHashMap<>();

    /** History chain is tried in order — first non-empty result wins. */
    private final List<StockFetcher> chain = List.of(
        new TwelvedataFetcher(),
        new AlphavantageFetcher()
    );

    private final QuoteFetcher quoteFetcher = new FinnhubFetcher();

    private final PriceCache cache = new PriceCache();

    /** Cached history younger than this is used without calling a provider. */
    static final Duration CACHE_TTL = Duration.ofHours(6);

    /** Ticker → company name of the stocks every user starts with. */
    public static final Map<String, String> DEFAULT_STOCKS = new LinkedHashMap<>();
    static {
        DEFAULT_STOCKS.put("AAPL",  "Apple Inc");
        DEFAULT_STOCKS.put("MSFT",  "Microsoft Corp");
        DEFAULT_STOCKS.put("AMZN",  "Amazon.com Inc");
        DEFAULT_STOCKS.put("GOOGL", "Alphabet Inc Class A");
        DEFAULT_STOCKS.put("TSLA",  "Tesla Inc");
    }

    // ----------------------------------------------------------------
    // Loading
    // ----------------------------------------------------------------

    /** Loads history for the default stocks, then live quotes. Blocking. */
    public void loadAll() {
        loadAll(DEFAULT_STOCKS);
    }

    /** Loads history for each ticker → company name, then live quotes. Blocking. */
    public void loadAll(Map<String, String> stocks) {
        logChainStatus();
        for (Map.Entry<String, String> s : stocks.entrySet()) load(s.getKey(), s.getValue());
        refreshQuotes();
    }

    /** Loads one stock's history (no live quote). Returns false if no price at all could be found. Blocking. */
    public boolean load(String symbol, String companyName) {
        List<PricePoint> history = loadHistory(symbol);
        if (!history.isEmpty()) {
            stockMap.put(symbol, new Stock(symbol, companyName, history));
            return true;
        }
        Optional<Double> cached = tryCache(() -> cache.loadPrice(symbol), Optional.empty());
        if (cached.isPresent()) {
            stockMap.put(symbol, new Stock(symbol, companyName, List.of(new PricePoint(LocalDate.now(), cached.get()))));
            log.warn("{}: no history from providers or cache, using last recorded price ${}", symbol, cached.get());
            return true;
        }
        log.warn("{}: no history and no recorded price, skipping", symbol);
        return false;
    }

    /** Fresh cache → providers → stale cache. Empty if all fail. */
    private List<PricePoint> loadHistory(String symbol) {
        Optional<Instant> fetchedAt = tryCache(() -> cache.lastFetched(symbol), Optional.empty());
        if (fetchedAt.isPresent() && fetchedAt.get().isAfter(Instant.now().minus(CACHE_TTL))) {
            List<PricePoint> fresh = tryCache(() -> cache.loadHistory(symbol), List.of());
            if (!fresh.isEmpty()) {
                log.info("{}: {} days of history from the DB cache (fetched {})", symbol, fresh.size(), fetchedAt.get());
                return fresh;
            }
        }

        List<PricePoint> fetched = fetchWithChain(symbol);
        if (!fetched.isEmpty()) {
            tryCache(() -> { cache.saveHistory(symbol, fetched); cache.savePrice(symbol, fetched.get(fetched.size() - 1).close()); return null; }, null);
            return fetched;
        }

        List<PricePoint> stale = tryCache(() -> cache.loadHistory(symbol), List.of());
        if (!stale.isEmpty()) {
            log.warn("{}: providers failed, using cached history up to {}", symbol, stale.get(stale.size() - 1).date());
        }
        return stale;
    }

    /** Updates the live price of every loaded stock. Returns how many were updated. Blocking. */
    public int refreshQuotes() {
        if (!quoteFetcher.isConfigured()) return 0;
        int updated = 0;
        for (String key : stockMap.keySet()) {
            if (refreshQuote(key)) updated++;
        }
        return updated;
    }

    /**
     * Fetches a fresh live price for one stock, e.g. right before a trade.
     * Returns false if no quote could be fetched (the stock keeps its last known price).
     */
    public boolean refreshQuote(String symbol) {
        Stock stock = stockMap.get(symbol);
        if (stock == null || !quoteFetcher.isConfigured()) return false;
        OptionalDouble quote = quoteFetcher.fetchQuote(symbol);
        if (quote.isEmpty()) return false;
        stock.setLivePrice(quote.getAsDouble());
        tryCache(() -> { cache.savePrice(symbol, quote.getAsDouble()); return null; }, null);
        return true;
    }

    private List<PricePoint> fetchWithChain(String symbol) {
        for (StockFetcher fetcher : chain) {
            if (!fetcher.isConfigured()) continue;
            List<PricePoint> result = fetcher.fetchHistory(symbol);
            if (!result.isEmpty()) {
                log.info("{}: {} days of history from {}", symbol, result.size(), fetcher.providerName());
                return result;
            }
            log.info("{}: no data from {}, trying next provider", symbol, fetcher.providerName());
        }
        return Collections.emptyList();
    }

    private void logChainStatus() {
        for (StockFetcher f : chain) {
            log.info("History provider {}: {}", f.providerName(), f.isConfigured() ? "active" : "skipped (no key)");
        }
        log.info("Quote provider {}: {}", quoteFetcher.providerName(), quoteFetcher.isConfigured() ? "active" : "skipped (no key)");
        if (chain.stream().noneMatch(StockFetcher::isConfigured)) {
            log.warn("No history API keys configured — using DB cache only");
        }
    }

    // ----------------------------------------------------------------
    // Accessors
    // ----------------------------------------------------------------

    /** The loaded stock with this ticker, or null. */
    public Stock getStock(String symbol) { return stockMap.get(symbol); }

    /** Tickers of loaded stocks, sorted. */
    public List<String> getSymbols() {
        List<String> symbols = new ArrayList<>(stockMap.keySet());
        Collections.sort(symbols);
        return symbols;
    }

    /** Ticker → latest price for every loaded stock. */
    public Map<String, Double> latestPrices() {
        Map<String, Double> prices = new HashMap<>();
        for (Stock s : stockMap.values()) prices.put(s.getSymbol(), s.getLatestPrice());
        return prices;
    }

    // ----------------------------------------------------------------
    // DB cache — failures are logged, never fatal (the app works without MySQL data)
    // ----------------------------------------------------------------

    @FunctionalInterface
    private interface CacheCall<T> { T call() throws SQLException; }

    private static <T> T tryCache(CacheCall<T> call, T fallback) {
        try {
            return call.call();
        } catch (SQLException e) {
            log.warn("Price cache unavailable: {}", e.getMessage());
            return fallback;
        }
    }
}
