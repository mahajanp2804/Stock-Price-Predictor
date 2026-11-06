package com.capstone.ui;

import com.capstone.model.Stock;
import com.capstone.prediction.PredictionModule;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/** "Predict Prices" tab: indicators plus an N-day forecast. CPU only, so it runs on the UI thread. */
class PredictPricesPanel extends JPanel {

    PredictPricesPanel(AppContext ctx) {
        super(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        JPanel inputRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JComboBox<String> stockCombo = Ui.stockCombo(ctx.stocks());
        JTextField daysField = new JTextField(5);
        JButton predictBtn = new JButton("Predict");
        inputRow.add(new JLabel("Stock:")); inputRow.add(stockCombo);
        inputRow.add(new JLabel("Days ahead:")); inputRow.add(daysField);
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
            Stock stock = ctx.stocks().getStockByName(name);
            if (stock == null) { Ui.showError(ctx.frame(), "Stock data unavailable."); return; }
            resultArea.setText(report(stock.getPriceHistory(), days));
        });
    }

    private static String report(List<Double> hist, int days) {
        List<Double> prices = PredictionModule.predictFuturePrices(hist, days);
        double sma5  = PredictionModule.sma(hist, 5);
        double sma20 = PredictionModule.sma(hist, Math.min(20, hist.size()));
        double ema14 = PredictionModule.ema(hist, Math.min(14, hist.size()));
        double rsi   = PredictionModule.rsi(hist, Math.min(14, hist.size()-1));
        StringBuilder sb = new StringBuilder();
        sb.append("=== Indicators ===\n");
        sb.append(String.format("  SMA-5  : $%.2f%n", sma5));
        sb.append(String.format("  SMA-20 : $%.2f%n", sma20));
        sb.append(String.format("  EMA-14 : $%.2f%n", ema14));
        sb.append(String.format("  RSI-14 : %.1f  %s%n", rsi, rsi > 70 ? "(Overbought)" : rsi < 30 ? "(Oversold)" : "(Neutral)"));
        sb.append("\n=== Predicted Prices ===\n");
        for (int i = 0; i < prices.size(); i++) sb.append(String.format("  Day +%-3d: $%.2f%n", i+1, prices.get(i)));
        sb.append("\n⚠ EDUCATIONAL USE ONLY. Not for real investment decisions.");
        return sb.toString();
    }
}
