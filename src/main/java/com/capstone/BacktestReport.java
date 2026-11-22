package com.capstone;

import com.capstone.data.StockDataModule;
import com.capstone.db.DBConnection;
import com.capstone.model.PricePoint;
import com.capstone.model.Stock;
import com.capstone.prediction.BacktestResult;
import com.capstone.prediction.Backtester;
import com.capstone.prediction.Forecasters;

import java.util.ArrayList;
import java.util.List;

/**
 * Prints how every forecasting model did on each stock's history, as Markdown tables.
 * Uses the same config, providers and DB cache as the app.
 *
 *   java -cp target/stock-trading-app.jar com.capstone.BacktestReport [days ahead …]
 *
 * Defaults to 1 and 5 trading days ahead.
 */
public final class BacktestReport {

    private BacktestReport() {}

    public static void main(String[] args) {
        List<Integer> horizons = new ArrayList<>();
        for (String a : args) horizons.add(Integer.parseInt(a));
        if (horizons.isEmpty()) horizons = List.of(1, 5);

        StockDataModule data = new StockDataModule();
        data.loadAll();
        try {
            for (int h : horizons) System.out.println(table(data, h));
        } finally {
            DBConnection.shutdown();
        }
    }

    static String table(StockDataModule data, int horizon) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%n### %d trading day%s ahead — average error (%% of price), up/down right%n%n", horizon, horizon == 1 ? "" : "s"));
        sb.append("| Symbol | Forecasts | Data |");
        Forecasters.ALL.forEach(f -> sb.append(' ').append(f.name()).append(" |"));
        sb.append("\n|---|---|---|");
        Forecasters.ALL.forEach(f -> sb.append("---|"));
        sb.append('\n');
        for (String symbol : data.getSymbols()) {
            Stock stock = data.getStock(symbol);
            List<PricePoint> history = stock.getHistory();
            List<BacktestResult> results = Backtester.compareAll(stock.getPriceHistory(), horizon);
            double best = results.stream().mapToDouble(BacktestResult::mae).min().orElse(Double.NaN);
            sb.append(String.format("| %s | %d | %s → %s |", symbol, results.get(0).samples(),
                history.get(0).date(), history.get(history.size() - 1).date()));
            for (BacktestResult r : results) {
                if (r.samples() == 0) { sb.append(" n/a |"); continue; }
                String cell = String.format("%.2f%%", r.mape());
                if (r.mae() == best) cell = "**" + cell + "**";
                if (!Double.isNaN(r.directionAccuracy())) cell += String.format(", %.0f%%", r.directionAccuracy() * 100);
                sb.append(' ').append(cell).append(" |");
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}
