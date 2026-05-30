package com.capstone.data;

import com.capstone.db.AppConfig;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * Fetches daily price history from Alpha Vantage.
 *
 * Endpoint: GET /query?function=TIME_SERIES_DAILY
 * Free tier: 25 requests/day, 15-min delayed data, 20+ years of history.
 * Response:  { "Time Series (Daily)": { "2024-01-01": { "4. close": "..." } } }
 *
 * Used as the last-resort API fallback before hitting the DB cache.
 */
public class AlphavantageFetcher implements StockFetcher {

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
        List<Double> history = new ArrayList<>();
        try {
            String urlStr = BASE
                + "?function=TIME_SERIES_DAILY"
                + "&symbol=" + symbol
                + "&outputsize=compact"          // compact = last 100 trading days
                + "&apikey=" + AppConfig.getAlphavantageKey();

            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();

            JSONObject json = new JSONObject(sb.toString());

            if (json.has("Note") || json.has("Information")) {
                // Rate limit hit
                System.out.println("[AlphaVantage] Rate limit reached for " + symbol);
                return history;
            }

            String key = "Time Series (Daily)";
            if (!json.has(key)) {
                System.out.println("[AlphaVantage] No time series data for " + symbol);
                return history;
            }

            JSONObject series = json.getJSONObject(key);

            // Dates come as keys — sort ascending (TreeMap natural order)
            TreeMap<String, JSONObject> sorted = new TreeMap<>();
            for (String date : series.keySet()) {
                sorted.put(date, series.getJSONObject(date));
            }

            // Take the last MAX_DAYS entries
            List<String> dates = new ArrayList<>(sorted.keySet());
            int start = Math.max(0, dates.size() - MAX_DAYS);
            for (int i = start; i < dates.size(); i++) {
                double close = sorted.get(dates.get(i)).getDouble("4. close");
                history.add(close);
            }

        } catch (Exception e) {
            System.out.println("[AlphaVantage] Error for " + symbol + ": " + e.getMessage());
        }
        return history;
    }
}
