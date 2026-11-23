package com.capstone.portfolio;

import com.capstone.model.Money;
import com.capstone.model.Transaction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PortfolioLedgerTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 11, 2, 10, 0);

    private static Transaction buy(String s, int q, String price) { return txn("BUY", s, q, price); }
    private static Transaction sell(String s, int q, String price) { return txn("SELL", s, q, price); }
    private static Transaction txn(String type, String s, int q, String price) {
        BigDecimal p = new BigDecimal(price).setScale(Money.PRICE_SCALE);
        return new Transaction(T, type, s, q, p, Money.total(p, q));
    }
    private static BigDecimal bd(String s) { return new BigDecimal(s); }

    @Test
    @DisplayName("selling above average cost realises the gain on those shares only")
    void realisedGain() {
        PortfolioLedger.Result r = PortfolioLedger.replay(List.of(
            buy("AAPL", 10, "100"), buy("AAPL", 10, "120"),      // avg 110
            sell("AAPL", 5, "130")));                             // (130 - 110) × 5
        assertEquals(bd("100.00"), r.realizedPnL());
        assertEquals(bd("100.00"), r.entries().get(2).realizedPnL());
        assertNull(r.entries().get(0).realizedPnL());
        PortfolioLedger.Position aapl = r.positions().get("AAPL");
        assertEquals(15, aapl.quantity());
        assertEquals(bd("110.0000"), aapl.avgCost(), "selling keeps the average cost");
        assertEquals(bd("1650.00"), r.openCostBasis());
    }

    @Test
    @DisplayName("a loss is negative, and selling everything closes the position")
    void realisedLossClosesPosition() {
        PortfolioLedger.Result r = PortfolioLedger.replay(List.of(buy("TSLA", 4, "250"), sell("TSLA", 4, "200")));
        assertEquals(bd("-200.00"), r.realizedPnL());
        assertTrue(r.positions().isEmpty());
        assertEquals(bd("-200.00"), r.netCashFlow());
    }

    @Test
    @DisplayName("each symbol keeps its own average; cash flow nets buys against sells")
    void perSymbol() {
        PortfolioLedger.Result r = PortfolioLedger.replay(List.of(
            buy("AAPL", 10, "100"), buy("MSFT", 2, "400"), sell("AAPL", 10, "90"), sell("MSFT", 1, "450")));
        assertEquals(bd("-50.00"), r.realizedPnL());            // -100 on AAPL, +50 on MSFT
        assertEquals(bd("-450.00"), r.netCashFlow());           // -1000 - 800 + 900 + 450
        assertEquals(1, r.positions().get("MSFT").quantity());
        assertEquals(List.of("MSFT"), List.copyOf(r.positions().keySet()));
    }

    @Test
    @DisplayName("matches PortfolioManager's rounding: average to 4 dp, cash to the cent")
    void rounding() {
        PortfolioLedger.Result r = PortfolioLedger.replay(List.of(
            buy("AAPL", 3, "100.0001"), buy("AAPL", 7, "100.0002"), sell("AAPL", 3, "100.0005")));
        assertEquals(Money.averagePrice(bd("100.0001"), 3, bd("100.0002"), 7), r.positions().get("AAPL").avgCost());
        assertEquals(Money.total(bd("100.0005"), 3).subtract(Money.total(r.positions().get("AAPL").avgCost(), 3)),
            r.realizedPnL());
    }

    @Test
    @DisplayName("no trades → nothing realised, nothing held")
    void empty() {
        PortfolioLedger.Result r = PortfolioLedger.replay(List.of());
        assertEquals(0, r.realizedPnL().signum());
        assertTrue(r.positions().isEmpty() && r.entries().isEmpty());
    }
}
