package com.capstone.data;

import com.capstone.db.AppConfig;
import org.json.JSONObject;
import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Fetches daily price history from Finnhub.
 *
 * Endpoint: GET /stock/candle
 * Free tier: 60 requests/minute, ~15-min delayed data.
 * Response:  { "c": [...closing prices], "s": "ok" }
 *
 * Candles require UNIX timestamps for from/to.
 * We request the last HISTORY_DAYS calendar days.
 */
public class FinnhubFetcher implements StockFetcher {

    private static final String BASE = "https://finnhub.io/api/v1/stock/candle";
    private static final int HISTORY_DAYS = 30;

    @Override
    public String providerName() { return "Finnhub"; }

    @Override
    public boolean isConfigured() {
        String key = AppConfig.getFinnhubKey();
        return key != null && !key.isBlank() && !key.startsWith("YOUR_");
    }

    @Override
    public List<Double> fetchHistory(String symbol) {
        List<Double> history = new ArrayList<>();
        try {
            long to   = LocalDate.now().atStartOfDay().toEpochSecond(ZoneOffset.UTC);
            long from = LocalDate.now().minusDays(HISTORY_DAYS)
                                       .atStartOfDay().toEpochSecond(ZoneOffset.UTC);

            String urlStr = BASE
                + "?symbol=" + symbol
                + "&resolution=D"
                + "&from=" + from
                + "&to=" + to
                + "&token=" + AppConfig.getFinnhubKey();

            String raw = get(urlStr);
            JSONObject json = new JSONObject(raw);

            if ("ok".equals(json.optString("s")) && json.has("c")) {
                JSONArray closes = json.getJSONArray("c");
                for (int i = 0; i < closes.length(); i++) {
                    history.add(closes.getDouble(i));
                }
            } else {
                System.out.println("[Finnhub] No data for " + symbol
                    + " — status: " + json.optString("s"));
            }
        } catch (Exception e) {
            System.out.println("[Finnhub] Error for " + symbol + ": " + e.getMessage());
        }
        return history;
    }

    private String get(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        reader.close();
        return sb.toString();
    }
}
