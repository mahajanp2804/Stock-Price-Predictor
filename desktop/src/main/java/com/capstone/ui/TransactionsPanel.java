package com.capstone.ui;

import com.capstone.portfolio.PortfolioAnalytics;
import com.capstone.portfolio.PortfolioLedger;
import com.capstone.model.Transaction;

import javax.swing.*;
import java.awt.*;
import java.time.format.DateTimeFormatter;

/** "Transactions" tab: the user's trade history, newest first, with realised P&L on each sell. */
class TransactionsPanel extends JPanel {

    private static final DateTimeFormatter TXN_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    TransactionsPanel(AppContext ctx) {
        super(new BorderLayout());
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setFont(new Font("Monospaced", Font.PLAIN, 12));
        area.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> {
            refreshBtn.setEnabled(false);
            UiTasks.run(() -> new PortfolioAnalytics(ctx.portfolio(), ctx.stocks()).report(ctx.userId()),
                report -> {
                    refreshBtn.setEnabled(true);
                    StringBuilder sb = new StringBuilder();
                    for (PortfolioLedger.Entry en : report.transactions()) {
                        Transaction t = en.transaction();
                        sb.append(String.format("[%s] %-4s  %-6s  x%-5d @ $%,10.2f  =  $%,11.2f",
                            TXN_TIME.format(t.timestamp()), t.type(), t.stockName(), t.quantity(), t.price(), t.total()));
                        if (en.realizedPnL() != null) sb.append("   realised ").append(PortfolioPanel.signed(en.realizedPnL()));
                        sb.append('\n');
                    }
                    if (!report.transactions().isEmpty()) {
                        sb.append("\nTotal realised P&L: ").append(PortfolioPanel.signed(report.summary().realizedPnL()));
                    }
                    area.setText(report.transactions().isEmpty() ? "No transactions yet." : sb.toString());
                },
                err -> { refreshBtn.setEnabled(true); Ui.showError(ctx.frame(), "Could not load transactions: " + err.getMessage()); });
        });
        add(new JScrollPane(area), BorderLayout.CENTER);
        add(refreshBtn, BorderLayout.SOUTH);
    }
}
