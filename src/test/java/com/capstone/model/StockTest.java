package com.capstone.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StockTest {

    private static final LocalDate START = LocalDate.of(2026, 9, 1);

    /** One close per calendar day starting at START: 100, 101, 102 … */
    private static Stock daily(int days) {
        List<PricePoint> h = new ArrayList<>();
        for (int i = 0; i < days; i++) h.add(new PricePoint(START.plusDays(i), 100 + i));
        return new Stock("Test", h);
    }

    @Test
    @DisplayName("change is measured from the first close on or after the date")
    void changeSince() {
        Stock s = daily(61);                                  // 100 … 160
        double pct = s.percentChangeSince(START.plusDays(30)).getAsDouble();   // from 130 to 160
        assertEquals((160.0 - 130.0) / 130.0 * 100, pct, 1e-9);
    }

    @Test
    @DisplayName("change uses the live quote when there is one")
    void changeUsesLivePrice() {
        Stock s = daily(61);
        s.setLivePrice(143);
        assertEquals(10.0, s.percentChangeSince(START.plusDays(30)).getAsDouble(), 1e-9);
    }

    @Test
    @DisplayName("no change when history doesn't go back far enough or is a single cached price")
    void changeUnavailable() {
        assertTrue(daily(10).percentChangeSince(START.minusDays(1)).isEmpty());
        assertTrue(new Stock("Cached", List.of(new PricePoint(START, 100))).percentChangeSince(START).isEmpty());
    }

    @Test
    @DisplayName("closes are returned oldest first")
    void closes() {
        assertEquals(List.of(100.0, 101.0, 102.0), daily(3).getPriceHistory());
    }
}
