package com.capstone.data;

import com.capstone.db.AppConfig;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.OptionalDouble;

/**
 * Fetches the latest price from Finnhub.
 *
 * Endpoint: GET /quote
 * Free tier: 60 requests/minute, real-time US quotes.
 * Response:  { "c": current, "pc": previous close, "t": unix time, ... }
 *
 * Finnhub's /stock/candle (daily history) is not available on the free plan
 * (HTTP 403), so history comes from Twelve Data / Alpha Vantage instead.
 */
public class FinnhubFetcher implements QuoteFetcher {

    private static final Logger log = LoggerFactory.getLogger(FinnhubFetcher.class);
    private static final String BASE = "https://finnhub.io/api/v1/quote";

    @Override
    public String providerName() { return "Finnhub"; }

    @Override
    public boolean isConfigured() {
        String key = AppConfig.getFinnhubKey();
        return key != null && !key.isBlank() && !key.startsWith("YOUR_");
    }

    @Override
    public OptionalDouble fetchQuote(String symbol) {
        try {
            String raw = Http.get(BASE, Http.params("symbol", symbol, "token", AppConfig.getFinnhubKey()));
            OptionalDouble price = parseQuote(raw);
            if (price.isEmpty()) log.warn("Finnhub returned no quote for {}", symbol);
            return price;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return OptionalDouble.empty();
        } catch (Exception e) {
            log.warn("Finnhub quote failed for {}: {}", symbol, e.getMessage());
            return OptionalDouble.empty();
        }
    }

    /** Unknown symbols come back as all zeros, so a non-positive price means "no quote". */
    static OptionalDouble parseQuote(String json) {
        double c = new JSONObject(json).optDouble("c", 0);
        return c > 0 ? OptionalDouble.of(c) : OptionalDouble.empty();
    }
}
