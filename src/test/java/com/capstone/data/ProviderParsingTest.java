package com.capstone.data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Parses real-shaped provider responses without hitting the network. */
class ProviderParsingTest {

    @Test
    @DisplayName("Finnhub: current price is read from 'c'")
    void finnhubQuote() {
        String json = "{\"c\":338.32,\"d\":-6.31,\"dp\":-1.8309,\"h\":343.8,\"l\":335.82,\"o\":342.67,\"pc\":344.63,\"t\":1793653200}";
        assertEquals(338.32, FinnhubFetcher.parseQuote(json).getAsDouble());
    }

    @Test
    @DisplayName("Finnhub: unknown symbol (all zeros) means no quote")
    void finnhubUnknownSymbol() {
        assertTrue(FinnhubFetcher.parseQuote("{\"c\":0,\"d\":null,\"dp\":null,\"h\":0,\"l\":0,\"o\":0,\"pc\":0,\"t\":0}").isEmpty());
    }

    @Test
    @DisplayName("Twelve Data: newest-first values are returned oldest-first")
    void twelveData() {
        String json = "{\"meta\":{\"symbol\":\"AAPL\"},\"values\":["
            + "{\"datetime\":\"2026-11-02\",\"close\":\"338.32\"},"
            + "{\"datetime\":\"2026-10-30\",\"close\":\"344.63\"},"
            + "{\"datetime\":\"2026-10-29\",\"close\":\"340.10\"}],\"status\":\"ok\"}";
        assertEquals(List.of(340.10, 344.63, 338.32), TwelvedataFetcher.parseHistory(json));
    }

    @Test
    @DisplayName("Twelve Data: error response gives empty history")
    void twelveDataError() {
        assertTrue(TwelvedataFetcher.parseHistory("{\"code\":429,\"message\":\"limit\",\"status\":\"error\"}").isEmpty());
    }

    @Test
    @DisplayName("Alpha Vantage: dates sorted ascending, capped at 30 days")
    void alphaVantage() {
        StringBuilder sb = new StringBuilder("{\"Time Series (Daily)\":{");
        for (int d = 1; d <= 35; d++) {
            if (d > 1) sb.append(',');
            sb.append(String.format("\"2026-10-%02d\":{\"4. close\":\"%d.0\"}", d, 100 + d));
        }
        sb.append("}}");
        List<Double> h = AlphavantageFetcher.parseHistory(sb.toString().replace("2026-10-3", "2026-11-0"));
        assertEquals(30, h.size());
        assertEquals(106.0, h.get(0));
        assertEquals(135.0, h.get(29));
    }

    @Test
    @DisplayName("Alpha Vantage: missing series gives empty history")
    void alphaVantageMissing() {
        assertTrue(AlphavantageFetcher.parseHistory("{\"Information\":\"rate limit\"}").isEmpty());
    }
}
