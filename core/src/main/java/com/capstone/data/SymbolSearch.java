package com.capstone.data;

import com.capstone.db.AppConfig;
import com.capstone.model.WatchItem;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Finds US stocks and ETFs by ticker or company name, using Finnhub's symbol search
 * (free plan). Blocks on the network — call off the Swing UI thread.
 */
public class SymbolSearch {

    private static final Logger log = LoggerFactory.getLogger(SymbolSearch.class);
    private static final String BASE = "https://finnhub.io/api/v1/search";
    private static final Set<String> TYPES = Set.of("Common Stock", "ETP");
    static final int MAX_RESULTS = 15;

    public boolean isConfigured() {
        String key = AppConfig.getFinnhubKey();
        return key != null && !key.isBlank() && !key.startsWith("YOUR_");
    }

    /** Matching stocks, best match first. Throws if the search itself fails. */
    public List<WatchItem> search(String query) throws IOException, InterruptedException {
        if (query == null || query.isBlank()) return List.of();
        String raw = Http.get(BASE, Http.params("q", query.trim(), "exchange", "US", "token", AppConfig.getFinnhubKey()));
        List<WatchItem> results = parse(raw);
        log.info("Symbol search '{}': {} result(s)", query.trim(), results.size());
        return results;
    }

    /** US common stocks and ETFs from a /search response, without duplicates. */
    static List<WatchItem> parse(String json) {
        List<WatchItem> items = new ArrayList<>();
        JSONArray result = new JSONObject(json).optJSONArray("result");
        if (result == null) return items;
        for (int i = 0; i < result.length() && items.size() < MAX_RESULTS; i++) {
            JSONObject r = result.getJSONObject(i);
            String symbol = r.optString("displaySymbol", r.optString("symbol"));
            if (!TYPES.contains(r.optString("type")) || symbol.isBlank()) continue;
            if (items.stream().anyMatch(w -> w.symbol().equals(symbol))) continue;
            String name = r.optString("description");
            items.add(new WatchItem(symbol, name.isBlank() ? symbol : name));
        }
        return items;
    }
}
