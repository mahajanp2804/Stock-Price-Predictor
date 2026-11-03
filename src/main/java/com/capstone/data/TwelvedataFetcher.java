package com.capstone.data;

import com.capstone.db.AppConfig;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Fetches daily price history from Twelve Data (primary history provider).
 *
 * Endpoint: GET /time_series
 * Free tier: 800 requests/day, 8 requests/minute.
 * Response:  { "values": [ { "close": "..." }, ... ] }  (newest first)
 */
public class TwelvedataFetcher implements StockFetcher {

    private static final Logger log = LoggerFactory.getLogger(TwelvedataFetcher.class);
    private static final String BASE = "https://api.twelvedata.com/time_series";
    private static final int HISTORY_DAYS = 30;

    @Override
    public String providerName() { return "Twelve Data"; }

    @Override
    public boolean isConfigured() {
        String key = AppConfig.getTwelvedataKey();
        return key != null && !key.isBlank() && !key.startsWith("YOUR_");
    }

    @Override
    public List<Double> fetchHistory(String symbol) {
        try {
            String raw = Http.get(BASE, Http.params(
                "symbol", symbol,
                "interval", "1day",
                "outputsize", String.valueOf(HISTORY_DAYS),
                "apikey", AppConfig.getTwelvedataKey()));
            List<Double> history = parseHistory(raw);
            if (history.isEmpty()) {
                log.warn("Twelve Data returned no values for {}: {}", symbol, new JSONObject(raw).optString("message"));
            }
            return history;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of();
        } catch (Exception e) {
            log.warn("Twelve Data failed for {}: {}", symbol, e.getMessage());
            return List.of();
        }
    }

    /** Closing prices oldest-first; empty for error responses. */
    static List<Double> parseHistory(String json) {
        List<Double> history = new ArrayList<>();
        JSONArray values = new JSONObject(json).optJSONArray("values");
        if (values == null) return history;
        // API returns newest-first — reverse to oldest-first
        for (int i = values.length() - 1; i >= 0; i--) {
            history.add(values.getJSONObject(i).getDouble("close"));
        }
        return history;
    }
}
