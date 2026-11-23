package com.capstone.portfolio;

import com.capstone.model.Money;
import com.capstone.model.PricePoint;
import com.capstone.model.Transaction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PortfolioHistoryTest {

    private static final LocalDate D1 = LocalDate.of(2026, 11, 2), D2 = D1.plusDays(1), D3 = D1.plusDays(2), D4 = D1.plusDays(3);

    private static Transaction txn(LocalDate day, String type, String s, int q, String price) {
        BigDecimal p = new BigDecimal(price).setScale(Money.PRICE_SCALE);
        return new Transaction(day.atTime(10, 0), type, s, q, p, Money.total(p, q));
    }
    private static BigDecimal bd(String s) { return new BigDecimal(s); }

    @Test
    @DisplayName("values cash plus shares at each day's close, applying trades on their day")
    void dailyValues() {
        List<Transaction> trades = List.of(txn(D1, "BUY", "AAPL", 10, "100"), txn(D3, "SELL", "AAPL", 5, "105"));
        Map<String, List<PricePoint>> closes = Map.of("AAPL", List.of(
            new PricePoint(D1, 101), new PricePoint(D2, 110), new PricePoint(D3, 106)));
        List<PortfolioHistory.ValuePoint> v = PortfolioHistory.build(trades, bd("10000.00"), closes, Map.of("AAPL", 120.0), D4);

        assertEquals(List.of(D1, D2, D3, D4), v.stream().map(PortfolioHistory.ValuePoint::date).toList());
        assertEquals(bd("10010.00"), v.get(0).total());   // 9000 cash + 10 × 101
        assertEquals(bd("10100.00"), v.get(1).total());   // 9000 + 10 × 110
        assertEquals(bd("10055.00"), v.get(2).total());   // 9525 + 5 × 106
        assertEquals(bd("10125.00"), v.get(3).total());   // today: 9525 + 5 × 120 (live)
        assertEquals(bd("9525.00"), v.get(3).cash());
    }

    @Test
    @DisplayName("days before any cached close use the last traded price")
    void noCloseYet() {
        List<PortfolioHistory.ValuePoint> v = PortfolioHistory.build(
            List.of(txn(D1, "BUY", "NEW", 2, "50")), bd("1000.00"), Map.of(), Map.of(), D2);
        assertEquals(bd("1000.00"), v.get(0).total());
        assertEquals(bd("1000.00"), v.get(1).total());
    }

    @Test
    @DisplayName("no trades → no series")
    void empty() {
        assertTrue(PortfolioHistory.build(List.of(), bd("10000.00"), Map.of(), Map.of(), D1).isEmpty());
    }
}
