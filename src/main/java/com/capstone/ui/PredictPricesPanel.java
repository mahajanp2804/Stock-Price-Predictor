package com.capstone.ui;

import com.capstone.model.PricePoint;
import com.capstone.model.Stock;
import com.capstone.prediction.BacktestResult;
import com.capstone.prediction.Backtester;
import com.capstone.prediction.Forecaster;
import com.capstone.prediction.Forecasters;
import com.capstone.prediction.PredictionModule;

import javax.swing.*;
import java.awt.*;
import java.util.Comparator;
import java.util.List;

/**
 * "Predict Prices" tab: backtests every model on this stock's history, shows how
 * accurate each was, then forecasts with the chosen (by default the most accurate)
 * model and an 80% range taken from its past errors. CPU only, so it runs on the UI thread.
 */
class PredictPricesPanel extends JPanel {

    static final int MAX_DAYS_AHEAD = 30;
    private static final String BEST = "Most accurate (backtested)";

    PredictPricesPanel(AppContext ctx) {
        super(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        JPanel inputRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JComboBox<String> stockCombo = Ui.stockCombo(ctx.stocks());
        JTextField daysField = new JTextField("5", 4);
        JComboBox<String> modelCombo = new JComboBox<>();
        modelCombo.addItem(BEST);
        for (Forecaster f : Forecasters.ALL) modelCombo.addItem(f.name());
        JButton predictBtn = new JButton("Predict");
        inputRow.add(new JLabel("Stock:")); inputRow.add(stockCombo);
        inputRow.add(new JLabel("Days ahead:")); inputRow.add(daysField);
        inputRow.add(new JLabel("Model:")); inputRow.add(modelCombo);
        inputRow.add(predictBtn);
        JTextArea resultArea = new JTextArea();
        resultArea.setEditable(false);
        resultArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        add(inputRow, BorderLayout.NORTH);
        add(new JScrollPane(resultArea), BorderLayout.CENTER);

        predictBtn.addActionListener(e -> {
            String name = (String) stockCombo.getSelectedItem(); if (name == null) return;
            int days;
            try { days = Integer.parseInt(daysField.getText().trim()); }
            catch (NumberFormatException ex) { Ui.showError(ctx.frame(), "Please enter a valid whole number for days."); return; }
            if (days < 1 || days > MAX_DAYS_AHEAD) { Ui.showError(ctx.frame(), "Days ahead must be between 1 and " + MAX_DAYS_AHEAD + "."); return; }
            Stock stock = ctx.stocks().getStock(name);
            if (stock == null) { Ui.showError(ctx.frame(), "Stock data unavailable."); return; }
            resultArea.setText(report(stock, days, (String) modelCombo.getSelectedItem()));
            resultArea.setCaretPosition(0);
        });
    }

    static String report(Stock stock, int days, String modelChoice) {
        List<PricePoint> history = stock.getHistory();
        List<Double> closes = stock.getPriceHistory();
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%s (%s) — %d trading days of closes, %s to %s%n%n",
            stock.getSymbol(), stock.getName(), history.size(), history.get(0).date(), history.get(history.size() - 1).date()));

        List<BacktestResult> results = Backtester.compareAll(closes, days);
        BacktestResult baseline = results.get(0);   // Forecasters.ALL starts with today's price
        if (baseline.samples() == 0) {
            sb.append(String.format("Not enough history to test the models %d day(s) ahead (need at least %d days).%n",
                days, PredictionModule.MODEL_WINDOW + days));
            return sb.append(indicators(closes)).append(disclaimer()).toString();
        }

        // --- How each model did ---
        sb.append(String.format("=== How each model did, %d trading day(s) ahead ===%n", days));
        sb.append(String.format("Replayed %d past forecasts made %s to %s, each using only the prices known that day.%n%n",
            baseline.samples(), history.get(baseline.firstOrigin()).date(), history.get(baseline.lastOrigin()).date()));
        sb.append(String.format("  %-24s %-17s %-17s %s%n", "Model", "Average error", "Up/down right", "vs today's price"));
        for (BacktestResult r : results) {
            String direction = Double.isNaN(r.directionAccuracy()) ? "n/a" : String.format("%.0f%%", r.directionAccuracy() * 100);
            String versus = r == baseline ? "(baseline)" : skill(r.skillVersus(baseline));
            sb.append(String.format("  %-24s %-17s %-17s %s%n", r.forecaster(),
                String.format("$%.2f (%.2f%%)", r.mae(), r.mape()), direction, versus));
        }
        BacktestResult best = results.stream().min(Comparator.comparingDouble(BacktestResult::mae)).orElseThrow();
        sb.append('\n');
        sb.append(best == baseline
            ? "No model beat simply assuming the price stays where it is. Up/down calls near 50% are no better than a coin flip.\n"
            : String.format("Most accurate: %s, %s than assuming the price stays the same.%n", best.forecaster(), skill(best.skillVersus(baseline))));

        // --- Forecast ---
        BacktestResult chosen = BEST.equals(modelChoice) ? best
            : results.stream().filter(r -> r.forecaster().equals(modelChoice)).findFirst().orElse(best);
        Forecaster forecaster = Forecasters.ALL.stream().filter(f -> f.name().equals(chosen.forecaster())).findFirst().orElseThrow();
        List<Double> forecast = forecaster.forecast(closes, days);
        sb.append(String.format("%n=== Forecast: %s ===%n", chosen.forecaster()));
        sb.append(String.format("From the %s close of $%.2f. The range is where 80%% of this model's past outcomes landed.%n%n",
            history.get(history.size() - 1).date(), closes.get(closes.size() - 1)));
        for (int d = 1; d <= days; d++) {
            double point = forecast.get(d - 1);
            double[] range = chosen.range(d, point);
            sb.append(String.format("  Day +%-3d  $%-9.2f ($%.2f – $%.2f)%n", d, point, range[0], range[1]));
        }

        return sb.append(indicators(closes)).append(disclaimer()).toString();
    }

    private static String skill(double skill) {
        return String.format("%.1f%% %s", Math.abs(skill) * 100, skill >= 0 ? "better" : "worse");
    }

    private static String indicators(List<Double> hist) {
        double sma5  = PredictionModule.sma(hist, 5);
        double sma20 = PredictionModule.sma(hist, Math.min(20, hist.size()));
        double ema14 = PredictionModule.ema(hist, Math.min(14, hist.size()));
        double rsi   = PredictionModule.rsi(hist, Math.min(14, hist.size()-1));
        StringBuilder sb = new StringBuilder("\n=== Indicators ===\n");
        sb.append(String.format("  SMA-5  : $%.2f%n", sma5));
        sb.append(String.format("  SMA-20 : $%.2f%n", sma20));
        sb.append(String.format("  EMA-14 : $%.2f%n", ema14));
        sb.append(String.format("  RSI-14 : %.1f  %s%n", rsi, rsi > 70 ? "(Overbought)" : rsi < 30 ? "(Oversold)" : "(Neutral)"));
        return sb.toString();
    }

    private static String disclaimer() {
        return "\n⚠ EDUCATIONAL USE ONLY. Past accuracy doesn't guarantee future accuracy. Not for real investment decisions.";
    }
}
