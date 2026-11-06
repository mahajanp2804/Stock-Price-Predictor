package com.capstone.ui;

import com.capstone.model.Stock;
import com.capstone.prediction.PredictionModule;

import javax.swing.*;
import java.awt.*;

/** "Predict Trend" tab: moving-average trend label. */
class TrendPanel extends JPanel {

    TrendPanel(AppContext ctx) {
        setLayout(new GridBagLayout());
        setBorder(BorderFactory.createEmptyBorder(20, 40, 20, 40));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(8, 8, 8, 8); gc.fill = GridBagConstraints.HORIZONTAL;
        JComboBox<String> stockCombo = Ui.stockCombo(ctx.stocks());
        JButton predictBtn = new JButton("Analyse Trend");
        JLabel resultLabel = new JLabel(" ");
        resultLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        gc.gridx=0;gc.gridy=0; add(new JLabel("Stock:"),gc);
        gc.gridx=1; add(stockCombo,gc);
        gc.gridx=1;gc.gridy=1; add(predictBtn,gc);
        gc.gridx=0;gc.gridy=2;gc.gridwidth=2; add(resultLabel,gc);

        predictBtn.addActionListener(e -> {
            String name = (String) stockCombo.getSelectedItem(); if (name == null) return;
            Stock stock = ctx.stocks().getStockByName(name);
            if (stock == null) { Ui.showError(ctx.frame(), "Stock data unavailable."); return; }
            String trend = PredictionModule.predictTrend(stock.getPriceHistory());
            resultLabel.setText(name + ": " + trend);
            resultLabel.setForeground(trend.contains("Up") ? Ui.GAIN : trend.contains("Down") ? Ui.LOSS : Color.DARK_GRAY);
        });
    }
}
