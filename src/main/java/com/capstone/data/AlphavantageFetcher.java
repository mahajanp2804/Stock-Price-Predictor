package com.capstone.data;

import com.capstone.db.AppConfig;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * Fetches daily price history from Alpha Vantage.
 *
 * Endpoint: GET /query?function=TIME_SERIES_DAILY
 * Free tier: 25 requests/day.
 * Response:  { "Time Series (Daily)": { "2024-01-01": { "4. close": "..." } } }
 *
 * Used as the history fallback before hitting the DB cache.
 */
public class AlphavantageFetcher implements StockFetcher {

    private static final Logger log = LoggerFactory.getLogger(AlphavantageFetcher.class);
    private static final String BASE  = "https://www.alphavantage.co/query";
    private static final int MAX_DAYS = 30;

    @Override
    public String providerName() { return "Alpha Vantage"; }

    @Override
    public boolean isConfigured() {
        String key = AppConfig.getAlphavantageKey();
        return key != null && !key.isBlank() && !key.startsWith("YOUR_");
    }

    @Override
    public List<Double> fetchHistory(String symbol) {
        try {
            String raw = Http.get(BASE, Http.params(
                "function", "TIME_SERIES_DAILY",
                "symbol", symbol,
                "outputsize", "compact",          // compact = last 100 trading days
                "apikey", AppConfig.getAlphavantageKey()));
            JSONObject json = new JSONObject(raw);
            if (json.has("Note") || json.has("Information")) {
                log.warn("Alpha Vantage rate limit reached for {}", symbol);
                return List.of();
            }
            List<Double> history = parseHistory(raw);
            if (history.isEmpty()) log.warn("Alpha Vantage returned no time series for {}", symbol);
            return history;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of();
        } catch (Exception e) {
            log.warn("Alpha Vantage failed for {}: {}", symbol, e.getMessage());
            return List.of();
        }
    }

    /** Last MAX_DAYS closing prices oldest-first; empty if the series is missing. */
    static List<Double> parseHistory(String json) {
        List<Double> history = new ArrayList<>();
        JSONObject series = new JSONObject(json).optJSONObject("Time Series (Daily)");
        if (series == null) return history;

        // Dates come as keys — sort ascending (TreeMap natural order)
        TreeMap<String, JSONObject> sorted = new TreeMap<>();
        for (String date : series.keySet()) sorted.put(date, series.getJSONObject(date));

        List<String> dates = new ArrayList<>(sorted.keySet());
        for (int i = Math.max(0, dates.size() - MAX_DAYS); i < dates.size(); i++) {
            history.add(sorted.get(dates.get(i)).getDouble("4. close"));
        }
        return history;
    }
}
