package com.capstone.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class MoneyTest {

    private static BigDecimal bd(String s) { return new BigDecimal(s); }

    @Test
    @DisplayName("price keeps 4 decimal places")
    void price() {
        assertEquals(bd("338.3200"), Money.price(338.32));
        assertEquals(bd("0.1235"), Money.price(0.12345));
    }

    @Test
    @DisplayName("total is exact to the cent (no double drift)")
    void total() {
        // 0.1 * 3 in double is 0.30000000000000004
        assertEquals(bd("0.30"), Money.total(Money.price(0.1), 3));
        assertEquals(bd("3383.20"), Money.total(bd("338.32"), 10));
        assertEquals(bd("0.01"), Money.total(bd("0.0050"), 1)); // half-up
    }

    @Test
    @DisplayName("average price is the weighted mean")
    void averagePrice() {
        assertEquals(bd("150.0000"), Money.averagePrice(bd("100"), 10, bd("200"), 10));
        assertEquals(bd("133.3333"), Money.averagePrice(bd("100"), 20, bd("200"), 10));
    }

    @Test
    @DisplayName("portfolio entry computes value and P&L")
    void portfolioEntry() {
        PortfolioEntry e = new PortfolioEntry("Apple", 10, bd("100.0000"), bd("110.5000"));
        assertEquals(bd("1105.00"), e.marketValue);
        assertEquals(bd("105.00"), e.unrealizedPnL);
        assertEquals(10.5, e.pnlPercent, 1e-9);

        PortfolioEntry loss = new PortfolioEntry("Tesla", 4, bd("250.0000"), bd("200.0000"));
        assertEquals(bd("-200.00"), loss.unrealizedPnL);
        assertEquals(-20.0, loss.pnlPercent, 1e-9);
    }

    @Test
    @DisplayName("stock prefers the live quote over the last close")
    void stockLivePrice() {
        Stock s = new Stock("Apple", java.util.List.of(100.0, 101.0));
        assertEquals(101.0, s.getLatestPrice());
        assertNull(s.getLiveUpdatedAt());
        s.setLivePrice(102.5);
        assertEquals(102.5, s.getLatestPrice());
        assertNotNull(s.getLiveUpdatedAt());
        s.setLivePrice(0); // ignored
        assertEquals(102.5, s.getLatestPrice());
    }
}
