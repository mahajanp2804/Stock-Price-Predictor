package com.capstone.portfolio;

import com.capstone.model.Money;
import com.capstone.model.Transaction;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Replays a user's trades in order with the same average-cost rules PortfolioManager
 * uses, to work out realised P&L for every sell, the cost basis of what's still held,
 * and the net cash that trading has moved. Pure — no database access.
 */
public final class PortfolioLedger {

    /** A trade with its realised P&L (null for buys). */
    public record Entry(Transaction transaction, BigDecimal realizedPnL) {}

    /** Shares still held after the replay, at their average cost. */
    public record Position(String symbol, int quantity, BigDecimal avgCost) {
        public BigDecimal costBasis() { return Money.total(avgCost, quantity); }
    }

    /**
     * @param entries      every trade, oldest first, with realised P&L on sells
     * @param positions    symbol → open position (sorted by symbol)
     * @param realizedPnL  total realised P&L across all sells
     * @param netCashFlow  money received from sells minus money spent on buys
     */
    public record Result(List<Entry> entries, Map<String, Position> positions,
                         BigDecimal realizedPnL, BigDecimal netCashFlow) {

        /** Total cost basis of the shares still held. */
        public BigDecimal openCostBasis() {
            return positions.values().stream().map(Position::costBasis).reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }

    private PortfolioLedger() {}

    /** @param oldestFirst the user's trades in the order they happened */
    public static Result replay(List<Transaction> oldestFirst) {
        Map<String, Position> positions = new TreeMap<>();
        List<Entry> entries = new ArrayList<>();
        BigDecimal realized = BigDecimal.ZERO.setScale(Money.CASH_SCALE);
        BigDecimal cash = BigDecimal.ZERO.setScale(Money.CASH_SCALE);

        for (Transaction t : oldestFirst) {
            Position p = positions.getOrDefault(t.stockName(), new Position(t.stockName(), 0, BigDecimal.ZERO));
            if ("BUY".equals(t.type())) {
                BigDecimal avg = p.quantity() == 0 ? t.price()
                    : Money.averagePrice(p.avgCost(), p.quantity(), t.price(), t.quantity());
                positions.put(t.stockName(), new Position(t.stockName(), p.quantity() + t.quantity(), avg));
                cash = cash.subtract(t.total());
                entries.add(new Entry(t, null));
            } else {
                // Selling doesn't change the average cost of the shares that remain
                BigDecimal pnl = t.total().subtract(Money.total(p.avgCost(), t.quantity()));
                int remaining = p.quantity() - t.quantity();
                if (remaining > 0) positions.put(t.stockName(), new Position(t.stockName(), remaining, p.avgCost()));
                else positions.remove(t.stockName());
                realized = realized.add(pnl);
                cash = cash.add(t.total());
                entries.add(new Entry(t, pnl));
            }
        }
        return new Result(Collections.unmodifiableList(entries), Collections.unmodifiableMap(positions), realized, cash);
    }
}
