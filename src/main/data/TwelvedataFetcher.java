package com.capstone.data;

import com.capstone.db.AppConfig;
import org.json.JSONObject;
import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Fetches daily price history from Twelve Data (original provider).
 *
 * Endpoint: GET /time_series
 * Free tier: 800 requests/day, 4-hour delayed data.
 * Response:  { "values": [ { "close": "..." }, ... ] }
 */
public class TwelvedataFetcher implements StockFetcher {

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
        List<Double> history = new ArrayList<>();
        try {
            String urlStr = BASE
                + "?symbol=" + symbol
                + "&interval=1day"
                + "&outputsize=" + HISTORY_DAYS
                + "&apikey=" + AppConfig.getTwelvedataKey();

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
            if (json.has("values")) {
                JSONArray values = json.getJSONArray("values");
                // API returns newest-first — reverse to oldest-first
                for (int i = values.length() - 1; i >= 0; i--) {
                    history.add(values.getJSONObject(i).getDouble("close"));
                }
            } else {
                System.out.println("[TwelveData] No values for " + symbol
                    + (json.has("message") ? " — " + json.getString("message") : ""));
            }
        } catch (Exception e) {
            System.out.println("[TwelveData] Error for " + symbol + ": " + e.getMessage());
        }
        return history;
    }
}
