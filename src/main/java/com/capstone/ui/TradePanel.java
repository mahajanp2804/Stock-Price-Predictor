package com.capstone.ui;

import com.capstone.model.Stock;

import javax.swing.*;
import java.awt.*;

/** "Buy" and "Sell" tabs: fetch a fresh quote, then trade at it. */
class TradePanel extends JPanel {

    TradePanel(AppContext ctx, boolean isBuy) {
        setLayout(new GridBagLayout());
        setBorder(BorderFactory.createEmptyBorder(20, 40, 20, 40));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(7, 7, 7, 7); gc.fill = GridBagConstraints.HORIZONTAL;

        JComboBox<String> stockCombo = Ui.stockCombo(ctx.stocks());
        JTextField qtyField = new JTextField(14);
        JButton tradeButton = new JButton(isBuy ? "Buy" : "Sell");
        JLabel resultLabel = new JLabel(" ");
        resultLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));

        gc.gridx=0;gc.gridy=0; add(new JLabel("Stock:"),gc);
        gc.gridx=1; add(stockCombo,gc);
        gc.gridx=0;gc.gridy=1; add(new JLabel("Quantity:"),gc);
        gc.gridx=1; add(qtyField,gc);
        gc.gridx=1;gc.gridy=2; add(tradeButton,gc);
        gc.gridx=0;gc.gridy=3;gc.gridwidth=2; add(resultLabel,gc);

        tradeButton.addActionListener(e -> {
            String name = (String) stockCombo.getSelectedItem(); if (name == null) return;
            int qty;
            try { qty = Integer.parseInt(qtyField.getText().trim()); }
            catch (NumberFormatException ex) { Ui.showError(ctx.frame(), "Please enter a valid whole number for quantity."); return; }

            tradeButton.setEnabled(false);
            resultLabel.setText("Getting latest price…");
            UiTasks.run(() -> {
                    boolean fresh = ctx.stocks().refreshQuote(name);
                    Stock stock = ctx.stocks().getStockByName(name);
                    if (stock == null) return "Stock data not available for " + name;
                    double price = stock.getLatestPrice();
                    String msg = isBuy ? ctx.portfolio().buyStock(ctx.userId(), name, qty, price)
                                       : ctx.portfolio().sellStock(ctx.userId(), name, qty, price);
                    return fresh ? msg : msg + "<br><i>Live quote unavailable — used last known price.</i>";
                },
                msg -> {
                    resultLabel.setText("<html>" + msg + "</html>");
                    tradeButton.setEnabled(true);
                    ctx.afterTrade().run();
                },
                err -> {
                    resultLabel.setText(" ");
                    tradeButton.setEnabled(true);
                    Ui.showError(ctx.frame(), (isBuy ? "Buy" : "Sell") + " failed: " + err.getMessage());
                });
        });
    }
}
