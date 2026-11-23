package com.capstone.portfolio;

import com.capstone.model.Money;
import com.capstone.model.PricePoint;
import com.capstone.model.Transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Daily value of a portfolio (cash + shares at that day's close) from the first trade
 * until today. Pure — no database access.
 */
public final class PortfolioHistory {

    public record ValuePoint(LocalDate date, BigDecimal cash, BigDecimal holdings, BigDecimal total) {}

    private PortfolioHistory() {}

    /**
     * @param oldestFirst  the user's trades in order
     * @param startingCash cash before the first trade
     * @param closes       symbol → daily closes (any order); missing days use the last earlier close
     * @param latest       symbol → current price, used for today's point
     * @param today        the last day of the series
     * @return one point per trading day (days with a close for any traded symbol), plus the
     *         first trade's day and today; empty if there are no trades
     */
    public static List<ValuePoint> build(List<Transaction> oldestFirst, BigDecimal startingCash,
                                         Map<String, List<PricePoint>> closes, Map<String, Double> latest, LocalDate today) {
        if (oldestFirst.isEmpty()) return List.of();
        LocalDate start = oldestFirst.get(0).timestamp().toLocalDate();

        Map<String, NavigableMap<LocalDate, Double>> bySymbol = new HashMap<>();
        TreeSet<LocalDate> days = new TreeSet<>(List.of(start, today));
        for (Map.Entry<String, List<PricePoint>> e : closes.entrySet()) {
            NavigableMap<LocalDate, Double> m = new TreeMap<>();
            for (PricePoint p : e.getValue()) {
                m.put(p.date(), p.close());
                if (!p.date().isBefore(start) && !p.date().isAfter(today)) days.add(p.date());
            }
            bySymbol.put(e.getKey(), m);
        }

        List<ValuePoint> out = new ArrayList<>();
        Map<String, Integer> held = new TreeMap<>();
        Map<String, BigDecimal> lastTradePrice = new HashMap<>();
        BigDecimal cash = startingCash;
        int next = 0;
        for (LocalDate day : days) {
            while (next < oldestFirst.size() && !oldestFirst.get(next).timestamp().toLocalDate().isAfter(day)) {
                Transaction t = oldestFirst.get(next++);
                int sign = "BUY".equals(t.type()) ? 1 : -1;
                held.merge(t.stockName(), sign * t.quantity(), Integer::sum);
                cash = sign > 0 ? cash.subtract(t.total()) : cash.add(t.total());
                lastTradePrice.put(t.stockName(), t.price());
            }
            BigDecimal holdings = BigDecimal.ZERO.setScale(Money.CASH_SCALE);
            for (Map.Entry<String, Integer> h : held.entrySet()) {
                if (h.getValue() <= 0) continue;
                BigDecimal price = priceOn(h.getKey(), day, day.equals(today), bySymbol, latest, lastTradePrice);
                holdings = holdings.add(Money.total(price, h.getValue()));
            }
            out.add(new ValuePoint(day, cash, holdings, cash.add(holdings)));
        }
        return out;
    }

    /** Today's live price, else the close on or before the day, else the last traded price. */
    private static BigDecimal priceOn(String symbol, LocalDate day, boolean isToday, Map<String, NavigableMap<LocalDate, Double>> closes,
                                      Map<String, Double> latest, Map<String, BigDecimal> lastTradePrice) {
        if (isToday && latest.containsKey(symbol)) return Money.price(latest.get(symbol));
        NavigableMap<LocalDate, Double> m = closes.get(symbol);
        Map.Entry<LocalDate, Double> close = m == null ? null : m.floorEntry(day);
        return close != null ? Money.price(close.getValue()) : lastTradePrice.get(symbol);
    }
}
