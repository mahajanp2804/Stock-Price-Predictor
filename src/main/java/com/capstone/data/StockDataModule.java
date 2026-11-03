package com.capstone.data;

import com.capstone.db.DBConnection;
import com.capstone.model.Stock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads stock prices.
 *
 * Daily history (for prediction), first provider with data wins:
 *   1. Twelve Data   (800 req/day)
 *   2. Alpha Vantage (25 req/day)
 *   3. DB cache      (last persisted price)
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
            List<Double> history = fetchWithChain(s.getValue());

            if (!history.isEmpty()) {
                stockMap.put(s.getKey(), new Stock(name, history));
                persistPriceToDB(name, history.get(history.size() - 1));
            } else {
                double cached = loadPriceFromDB(name);
                if (cached > 0) {
                    stockMap.put(s.getKey(), new Stock(name, List.of(cached)));
                    log.warn("{}: all history providers failed, using DB cache ${}", name, cached);
                } else {
                    log.warn("{}: all history providers failed and no DB cache, skipping", name);
                }
            }
        }
        refreshQuotes();
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
        persistPriceToDB(stock.getName(), quote.getAsDouble());
        return true;
    }

    private List<Double> fetchWithChain(String symbol) {
        for (StockFetcher fetcher : chain) {
            if (!fetcher.isConfigured()) continue;
            List<Double> result = fetcher.fetchHistory(symbol);
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
    // DB persistence (cache latest price for offline fallback)
    // ----------------------------------------------------------------

    private void persistPriceToDB(String stockName, double price) {
        String sql = "INSERT INTO stocks (stock_name, current_price) VALUES (?, ?) "
                   + "ON DUPLICATE KEY UPDATE current_price = VALUES(current_price)";
        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, stockName);
            ps.setBigDecimal(2, BigDecimal.valueOf(price));
            ps.executeUpdate();
        } catch (SQLException e) {
            log.warn("Could not persist price for {}: {}", stockName, e.getMessage());
        }
    }

    private double loadPriceFromDB(String stockName) {
        String sql = "SELECT current_price FROM stocks WHERE stock_name = ?";
        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, stockName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getBigDecimal("current_price").doubleValue();
            }
        } catch (SQLException e) {
            log.warn("Could not load cached price for {}: {}", stockName, e.getMessage());
        }
        return -1;
    }
}
