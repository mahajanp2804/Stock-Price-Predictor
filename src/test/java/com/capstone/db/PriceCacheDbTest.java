package com.capstone.db;

import com.capstone.model.PricePoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PriceCacheDbTest extends DbTestSupport {

    private final PriceCache cache = new PriceCache();
    private static final LocalDate D = LocalDate.of(2026, 11, 2);

    @Test
    @DisplayName("history round-trips oldest first, regardless of insert order")
    void roundTrip() throws Exception {
        cache.saveHistory("AAPL", List.of(new PricePoint(D.plusDays(1), 341.5), new PricePoint(D, 338.32)));
        assertEquals(List.of(new PricePoint(D, 338.32), new PricePoint(D.plusDays(1), 341.5)), cache.loadHistory("AAPL"));
        assertTrue(cache.loadHistory("TSLA").isEmpty());
    }

    @Test
    @DisplayName("saving again updates existing days and adds new ones")
    void upsert() throws Exception {
        cache.saveHistory("AAPL", List.of(new PricePoint(D, 338.32)));
        cache.saveHistory("AAPL", List.of(new PricePoint(D, 339.00), new PricePoint(D.plusDays(1), 341.5)));
        assertEquals(List.of(new PricePoint(D, 339.00), new PricePoint(D.plusDays(1), 341.5)), cache.loadHistory("AAPL"));
    }

    @Test
    @DisplayName("only the most recent MAX_DAYS are loaded")
    void limit() throws Exception {
        List<PricePoint> many = new ArrayList<>();
        for (int i = 0; i < PriceCache.MAX_DAYS + 20; i++) many.add(new PricePoint(D.minusDays(i), 100 + i));
        cache.saveHistory("AAPL", many);
        List<PricePoint> loaded = cache.loadHistory("AAPL");
        assertEquals(PriceCache.MAX_DAYS, loaded.size());
        assertEquals(D, loaded.get(loaded.size() - 1).date());
    }

    @Test
    @DisplayName("fetch time is recorded")
    void lastFetched() throws Exception {
        assertTrue(cache.lastFetched("AAPL").isEmpty());
        cache.saveHistory("AAPL", List.of(new PricePoint(D, 338.32)));
        Instant at = cache.lastFetched("AAPL").orElseThrow();
        assertTrue(Duration.between(at, Instant.now()).abs().toMinutes() < 5, "fetched_at " + at);
    }

    @Test
    @DisplayName("latest price is stored and updated")
    void price() throws Exception {
        assertTrue(cache.loadPrice("AAPL").isEmpty());
        cache.savePrice("AAPL", 338.32);
        cache.savePrice("AAPL", 340.1);
        assertEquals(340.1, cache.loadPrice("AAPL").orElseThrow());
    }
}
