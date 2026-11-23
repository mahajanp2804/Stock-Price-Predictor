package com.capstone.db;

import com.capstone.data.StockDataModule;
import com.capstone.model.PricePoint;
import com.capstone.portfolio.PortfolioAnalytics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PortfolioAnalyticsDbTest extends DbTestSupport {

    @Test
    @DisplayName("summary adds up, history ends at today's total, sells carry realised P&L")
    void report() throws Exception {
        assertEquals(AuthManager.RegisterResult.OK, new AuthManager().register("analyst", "password1"));
        int user = Integer.parseInt(scalar("SELECT id FROM users WHERE username='analyst'"));
        List<PricePoint> history = new ArrayList<>();
        for (int i = 40; i >= 0; i--) history.add(new PricePoint(LocalDate.now().minusDays(i), 100 + (40 - i)));
        new PriceCache().saveHistory("AAPL", history);            // last close 140

        PortfolioManager pm = new PortfolioManager();
        pm.buyStock(user, "AAPL", 10, 120);
        pm.sellStock(user, "AAPL", 4, 135);                        // realises (135 - 120) × 4 = 60

        StockDataModule data = new StockDataModule();              // nothing loaded → uses the DB cache
        PortfolioAnalytics.Report r = new PortfolioAnalytics(pm, data).report(user);
        PortfolioAnalytics.Summary s = r.summary();

        assertEquals(new BigDecimal("10000.00"), s.startingValue());
        assertEquals(new BigDecimal("9340.00"), s.cash());         // 10000 - 1200 + 540
        assertEquals(new BigDecimal("840.00"), s.holdingsValue()); // 6 × 140
        assertEquals(new BigDecimal("60.00"), s.realizedPnL());
        assertEquals(new BigDecimal("120.00"), s.unrealizedPnL()); // 840 - 6 × 120
        assertEquals(new BigDecimal("180.00"), s.totalReturn());
        assertEquals(1.8, s.totalReturnPercent(), 1e-9);

        assertEquals(new BigDecimal("60.00"), r.transactions().get(0).realizedPnL(), "newest first: the sell");
        assertNull(r.transactions().get(1).realizedPnL());
        assertEquals(s.total(), r.history().get(r.history().size() - 1).total());
        assertEquals(LocalDate.now(), r.history().get(r.history().size() - 1).date());
    }
}
