package com.capstone.portfolio;

import com.capstone.data.StockDataModule;
import com.capstone.db.PortfolioManager;
import com.capstone.db.PriceCache;
import com.capstone.model.Money;
import com.capstone.model.PricePoint;
import com.capstone.model.Stock;
import com.capstone.model.Transaction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;

/**
 * A user's portfolio performance: realised and unrealised P&L, total return since
 * their first trade, and daily value over time. Blocks on the DB — call it off the UI thread.
 */
public class PortfolioAnalytics {

    /**
     * @param startingValue  cash before the first trade (normally $10,000)
     * @param totalReturn    total − startingValue; equals realised + unrealised up to rounding
     */
    public record Summary(BigDecimal cash, BigDecimal holdingsValue, BigDecimal total, BigDecimal startingValue,
                          BigDecimal realizedPnL, BigDecimal unrealizedPnL, BigDecimal totalReturn, double totalReturnPercent) {}

    /** @param transactions newest first, each with realised P&L on sells */
    public record Report(Summary summary, List<PortfolioLedger.Entry> transactions, List<PortfolioHistory.ValuePoint> history) {}

    private final PortfolioManager portfolio;
    private final StockDataModule data;
    private final PriceCache cache = new PriceCache();

    public PortfolioAnalytics(PortfolioManager portfolio, StockDataModule data) {
        this.portfolio = portfolio;
        this.data = data;
    }

    public Report report(int userId) throws SQLException {
        List<Transaction> oldestFirst = new ArrayList<>(portfolio.viewTransactions(userId));
        Collections.reverse(oldestFirst);
        PortfolioLedger.Result ledger = PortfolioLedger.replay(oldestFirst);
        BigDecimal cash = portfolio.getUserBalance(userId);
        BigDecimal startingValue = cash.subtract(ledger.netCashFlow());

        // Daily closes for every symbol ever traded: loaded stocks first, else the DB cache
        Map<String, List<PricePoint>> closes = new HashMap<>();
        for (Transaction t : oldestFirst) {
            if (closes.containsKey(t.stockName())) continue;
            Stock s = data.getStock(t.stockName());
            closes.put(t.stockName(), s != null ? s.getHistory() : cache.loadHistory(t.stockName()));
        }
        Map<String, Double> latest = data.latestPrices();

        BigDecimal holdings = BigDecimal.ZERO.setScale(Money.CASH_SCALE);
        for (PortfolioLedger.Position p : ledger.positions().values()) {
            holdings = holdings.add(Money.total(currentPrice(p, latest, closes), p.quantity()));
        }
        BigDecimal total = cash.add(holdings);
        BigDecimal totalReturn = total.subtract(startingValue);
        double pct = startingValue.signum() == 0 ? 0
            : totalReturn.multiply(BigDecimal.valueOf(100)).divide(startingValue, 4, RoundingMode.HALF_UP).doubleValue();
        Summary summary = new Summary(cash, holdings, total, startingValue, ledger.realizedPnL(),
            holdings.subtract(ledger.openCostBasis()), totalReturn, pct);

        List<PortfolioLedger.Entry> newestFirst = new ArrayList<>(ledger.entries());
        Collections.reverse(newestFirst);
        List<PortfolioHistory.ValuePoint> history = PortfolioHistory.build(oldestFirst, startingValue, closes, latest, LocalDate.now());
        return new Report(summary, newestFirst, history);
    }

    /** Live price if loaded, else the last cached close, else what the shares cost. */
    private static BigDecimal currentPrice(PortfolioLedger.Position p, Map<String, Double> latest, Map<String, List<PricePoint>> closes) {
        if (latest.containsKey(p.symbol())) return Money.price(latest.get(p.symbol()));
        List<PricePoint> h = closes.get(p.symbol());
        return h != null && !h.isEmpty() ? Money.price(h.get(h.size() - 1).close()) : p.avgCost();
    }
}
