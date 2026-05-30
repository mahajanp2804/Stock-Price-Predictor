package com.capstone.data;

import com.capstone.db.DBConnection;
import com.capstone.model.Stock;

import java.sql.*;
import java.util.*;
import javax.swing.SwingWorker;

/**
 * Loads stock price history using a provider chain:
 *
 *   1. Finnhub       (primary  — 60 req/min,   ~15 min delay)
 *   2. Twelve Data   (fallback — 800 req/day,   4 hr  delay)
 *   3. Alpha Vantage (fallback — 25 req/day,    15 min delay)
 *   4. DB cache      (offline  — last persisted price)
 *
 * Only configured providers (non-empty key in config.properties) are tried.
 * Each stock is attempted independently so one bad ticker doesn't break others.
 */
public class StockDataModule {

    private final Map<String, Stock> stockMap = Collections.synchronizedMap(new HashMap<>());

    /** Chain is tried in order — first non-empty result wins. */
    private final List<StockFetcher> chain = List.of(
        new FinnhubFetcher(),
        new TwelvedataFetcher(),
        new AlphavantageFetcher()
    );

    private static final String[][] STOCKS = {
        {"Apple",     "AAPL"},
        {"Microsoft", "MSFT"},
        {"Amazon",    "AMZN"},
        {"Google",    "GOOGL"},
        {"Tesla",     "TSLA"}
    };

    // ----------------------------------------------------------------
    // Async load (called from GUI — never blocks the EDT)
    // ----------------------------------------------------------------

    public void populateStockDataAsync(Runnable onComplete) {
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                printChainStatus();
                for (String[] s : STOCKS) {
                    String name   = s[0];
                    String symbol = s[1];
                    List<Double> history = fetchWithChain(symbol);

                    if (!history.isEmpty()) {
                        stockMap.put(name.toLowerCase(), new Stock(name, history));
                        persistPriceToDB(name, history.get(history.size() - 1));
                    } else {
                        // All providers failed — fall back to DB cache
                        double cached = loadPriceFromDB(name);
                        if (cached > 0) {
                            stockMap.put(name.toLowerCase(), new Stock(name, List.of(cached)));
                            System.out.println("[StockDataModule] " + name
                                + " — all APIs failed, using DB cache $" + cached);
                        } else {
                            System.out.println("[StockDataModule] " + name
                                + " — all APIs failed and no DB cache. Skipping.");
                        }
                    }
                }
                return null;
            }

            @Override
            protected void done() {
                if (onComplete != null) onComplete.run();
            }
        }.execute();
    }

    // ----------------------------------------------------------------
    // Chain logic — try each provider in order, stop at first success
    // ----------------------------------------------------------------

    private List<Double> fetchWithChain(String symbol) {
        for (StockFetcher fetcher : chain) {
            if (!fetcher.isConfigured()) {
                System.out.println("[StockDataModule] Skipping "
                    + fetcher.providerName() + " — no API key configured.");
                continue;
            }
            System.out.println("[StockDataModule] Trying "
                + fetcher.providerName() + " for " + symbol + "...");
            List<Double> result = fetcher.fetchHistory(symbol);
            if (!result.isEmpty()) {
                System.out.println("[StockDataModule] Got "
                    + result.size() + " days from " + fetcher.providerName());
                return result;
            }
            System.out.println("[StockDataModule] "
                + fetcher.providerName() + " returned no data for " + symbol
                + " — trying next provider.");
        }
        return Collections.emptyList();
    }

    /** Logs which providers are active at startup. */
    private void printChainStatus() {
        System.out.println("[StockDataModule] Provider chain:");
        int active = 0;
        for (StockFetcher f : chain) {
            String status = f.isConfigured() ? "ACTIVE" : "skipped (no key)";
            System.out.println("  " + f.providerName() + " — " + status);
            if (f.isConfigured()) active++;
        }
        if (active == 0) {
            System.out.println("  WARNING: no API keys configured — will use DB cache only.");
        }
    }

    // ----------------------------------------------------------------
    // Accessors
    // ----------------------------------------------------------------

    public Stock getRealtimeStock(String name) { return stockMap.get(name.toLowerCase()); }
    public Stock getStockByName(String name)   { return stockMap.get(name.toLowerCase()); }
    public Set<String> getAllStockNames()       { return stockMap.keySet(); }

    // ----------------------------------------------------------------
    // DB persistence (cache latest price for offline fallback)
    // ----------------------------------------------------------------

    private void persistPriceToDB(String stockName, double price) {
        String sql = "INSERT INTO stocks (stock_name, current_price) VALUES (?, ?) "
                   + "ON DUPLICATE KEY UPDATE current_price = VALUES(current_price)";
        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, stockName);
            ps.setDouble(2, price);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.out.println("[StockDataModule] Could not persist price for " + stockName);
        }
    }

    private double loadPriceFromDB(String stockName) {
        String sql = "SELECT current_price FROM stocks WHERE stock_name = ?";
        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, stockName);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getDouble("current_price");
        } catch (SQLException e) {
            System.out.println("[StockDataModule] Could not load cache for " + stockName);
        }
        return -1;
    }
}
