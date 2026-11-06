package com.capstone.ui;

import com.capstone.model.Money;
import com.capstone.model.PortfolioEntry;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.Map;

/** "Portfolio" tab: holdings with colour-coded unrealised P&L. */
class PortfolioPanel extends JPanel {

    PortfolioPanel(AppContext ctx) {
        super(new BorderLayout());
        DefaultTableModel model = Ui.readOnlyModel("Stock", "Qty", "Avg Price", "Market Value", "Unrealised P&L", "P&L %");
        JTable table = Ui.table(model);
        table.getColumnModel().getColumn(4).setCellRenderer(Ui.pnlRenderer());
        table.getColumnModel().getColumn(5).setCellRenderer(Ui.pnlRenderer());

        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> {
            refreshBtn.setEnabled(false);
            Map<String, Double> live = ctx.stocks().latestPrices();
            UiTasks.run(() -> ctx.portfolio().viewPortfolioWithPnL(ctx.userId(), live),
                entries -> {
                    refreshBtn.setEnabled(true);
                    model.setRowCount(0);
                    if (entries.isEmpty()) { model.addRow(new Object[]{"No holdings yet.","","","","",""}); return; }
                    for (PortfolioEntry en : entries) {
                        model.addRow(new Object[]{en.stockName, en.quantity,
                            Money.format(en.avgPrice), Money.format(en.marketValue),
                            String.format("%s$%,.2f", en.unrealizedPnL.signum() >= 0 ? "+" : "", en.unrealizedPnL),
                            String.format("%+.1f%%", en.pnlPercent)});
                    }
                },
                err -> { refreshBtn.setEnabled(true); Ui.showError(ctx.frame(), "Could not load portfolio: " + err.getMessage()); });
        });

        add(new JScrollPane(table), BorderLayout.CENTER);
        add(refreshBtn, BorderLayout.SOUTH);
    }
}
