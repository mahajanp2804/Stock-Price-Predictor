package com.capstone.data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.capstone.model.PricePoint;

import java.time.LocalDate;
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
    @DisplayName("Twelve Data: newest-first values are returned oldest-first, with dates")
    void twelveData() {
        String json = "{\"meta\":{\"symbol\":\"AAPL\"},\"values\":["
            + "{\"datetime\":\"2026-11-02\",\"close\":\"338.32\"},"
            + "{\"datetime\":\"2026-10-30\",\"close\":\"344.63\"},"
            + "{\"datetime\":\"2026-10-29\",\"close\":\"340.10\"}],\"status\":\"ok\"}";
        assertEquals(List.of(
            new PricePoint(LocalDate.of(2026, 10, 29), 340.10),
            new PricePoint(LocalDate.of(2026, 10, 30), 344.63),
            new PricePoint(LocalDate.of(2026, 11, 2), 338.32)), TwelvedataFetcher.parseHistory(json));
    }

    @Test
    @DisplayName("Twelve Data: error response gives empty history")
    void twelveDataError() {
        assertTrue(TwelvedataFetcher.parseHistory("{\"code\":429,\"message\":\"limit\",\"status\":\"error\"}").isEmpty());
    }

    @Test
    @DisplayName("Alpha Vantage: every day returned, sorted by date ascending")
    void alphaVantage() {
        String json = "{\"Time Series (Daily)\":{"
            + "\"2026-11-02\":{\"4. close\":\"338.32\"},"
            + "\"2026-10-29\":{\"4. close\":\"340.10\"},"
            + "\"2026-10-30\":{\"4. close\":\"344.63\"}}}";
        List<PricePoint> h = AlphavantageFetcher.parseHistory(json);
        assertEquals(3, h.size());
        assertEquals(new PricePoint(LocalDate.of(2026, 10, 29), 340.10), h.get(0));
        assertEquals(new PricePoint(LocalDate.of(2026, 11, 2), 338.32), h.get(2));
    }

    @Test
    @DisplayName("Alpha Vantage: missing series gives empty history")
    void alphaVantageMissing() {
        assertTrue(AlphavantageFetcher.parseHistory("{\"Information\":\"rate limit\"}").isEmpty());
    }
}
