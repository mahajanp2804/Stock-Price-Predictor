package com.capstone.ui;

import com.capstone.model.Transaction;

import javax.swing.*;
import java.awt.*;
import java.time.format.DateTimeFormatter;

/** "Transactions" tab: the user's trade history, newest first. */
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
            UiTasks.run(() -> ctx.portfolio().viewTransactions(ctx.userId()),
                txns -> {
                    refreshBtn.setEnabled(true);
                    StringBuilder sb = new StringBuilder();
                    for (Transaction t : txns) {
                        sb.append(String.format("[%s] %-4s  %-10s  x%d @ $%.2f  =  $%.2f%n",
                            TXN_TIME.format(t.timestamp()), t.type(), t.stockName(), t.quantity(), t.price(), t.total()));
                    }
                    area.setText(txns.isEmpty() ? "No transactions yet." : sb.toString());
                },
                err -> { refreshBtn.setEnabled(true); Ui.showError(ctx.frame(), "Could not load transactions: " + err.getMessage()); });
        });
        add(new JScrollPane(area), BorderLayout.CENTER);
        add(refreshBtn, BorderLayout.SOUTH);
    }
}
