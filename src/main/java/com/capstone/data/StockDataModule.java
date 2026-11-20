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

    private static final Map<String, String> SYMBOLS = new LinkedHashMap<>();
    static {
        SYMBOLS.put("apple",     "AAPL");
        SYMBOLS.put("microsoft", "MSFT");
        SYMBOLS.put("amazon",    "AMZN");
        SYMBOLS.put("google",    "GOOGL");
        SYMBOLS.put("tesla",     "TSLA");
    }

    // ----------------------------------------------------------------
    // Loading
    // ----------------------------------------------------------------

    /** Loads history for every stock, then live quotes. Blocking. */
    public void loadAll() {
        logChainStatus();
        for (Map.Entry<String, String> s : SYMBOLS.entrySet()) {
            String name = displayName(s.getKey());
            List<PricePoint> history = loadHistory(name, s.getValue());
            if (!history.isEmpty()) {
                stockMap.put(s.getKey(), new Stock(name, history));
                continue;
            }
            Optional<Double> cached = tryCache(() -> cache.loadPrice(name), Optional.empty());
            if (cached.isPresent()) {
                stockMap.put(s.getKey(), new Stock(name, List.of(new PricePoint(LocalDate.now(), cached.get()))));
                log.warn("{}: no history from providers or cache, using last recorded price ${}", name, cached.get());
            } else {
                log.warn("{}: no history and no recorded price, skipping", name);
            }
        }
        refreshQuotes();
    }

    /** Fresh cache → providers → stale cache. Empty if all fail. */
    private List<PricePoint> loadHistory(String name, String symbol) {
        Optional<Instant> fetchedAt = tryCache(() -> cache.lastFetched(name), Optional.empty());
        if (fetchedAt.isPresent() && fetchedAt.get().isAfter(Instant.now().minus(CACHE_TTL))) {
            List<PricePoint> fresh = tryCache(() -> cache.loadHistory(name), List.of());
            if (!fresh.isEmpty()) {
                log.info("{}: {} days of history from the DB cache (fetched {})", symbol, fresh.size(), fetchedAt.get());
                return fresh;
            }
        }

        List<PricePoint> fetched = fetchWithChain(symbol);
        if (!fetched.isEmpty()) {
            tryCache(() -> { cache.saveHistory(name, fetched); cache.savePrice(name, fetched.get(fetched.size() - 1).close()); return null; }, null);
            return fetched;
        }

        List<PricePoint> stale = tryCache(() -> cache.loadHistory(name), List.of());
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
    public boolean refreshQuote(String name) {
        String key = name.toLowerCase();
        Stock stock = stockMap.get(key);
        if (stock == null || !quoteFetcher.isConfigured()) return false;
        OptionalDouble quote = quoteFetcher.fetchQuote(SYMBOLS.get(key));
        if (quote.isEmpty()) return false;
        stock.setLivePrice(quote.getAsDouble());
        tryCache(() -> { cache.savePrice(stock.getName(), quote.getAsDouble()); return null; }, null);
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

    public Stock getStockByName(String name) { return stockMap.get(name.toLowerCase()); }

    /** Display names (e.g. "Apple") of loaded stocks, sorted. */
    public List<String> getAllStockNames() {
        List<String> names = new ArrayList<>();
        for (Stock s : stockMap.values()) names.add(s.getName());
        Collections.sort(names);
        return names;
    }

    /** Display name → latest price for every loaded stock. */
    public Map<String, Double> latestPrices() {
        Map<String, Double> prices = new HashMap<>();
        for (Stock s : stockMap.values()) prices.put(s.getName(), s.getLatestPrice());
        return prices;
    }

    private static String displayName(String key) {
        return Character.toUpperCase(key.charAt(0)) + key.substring(1);
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
