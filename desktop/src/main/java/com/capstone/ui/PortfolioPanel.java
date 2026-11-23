package com.capstone.ui;

import com.capstone.model.Money;
import com.capstone.model.PortfolioEntry;
import com.capstone.model.PricePoint;
import com.capstone.portfolio.PortfolioAnalytics;
import com.capstone.portfolio.PortfolioHistory;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * "Portfolio" tab: a summary (cash, holdings, total return, realised and unrealised
 * P&L), holdings with colour-coded unrealised P&L, and total value over time.
 * Refreshes whenever the tab is shown.
 */
class PortfolioPanel extends JPanel {

    private final AppContext ctx;
    private final DefaultTableModel model = Ui.readOnlyModel("Stock", "Qty", "Avg Price", "Market Value", "Unrealised P&L", "P&L %");
    private final JLabel summary = new JLabel(" ");
    private final PriceChart chart = new PriceChart();
    private final JButton refreshBtn = new JButton("Refresh");

    record Data(List<PortfolioEntry> holdings, PortfolioAnalytics.Report report) {}

    PortfolioPanel(AppContext ctx) {
        super(new BorderLayout());
        this.ctx = ctx;
        JTable table = Ui.table(model);
        table.getColumnModel().getColumn(4).setCellRenderer(Ui.pnlRenderer());
        table.getColumnModel().getColumn(5).setCellRenderer(Ui.pnlRenderer());

        summary.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        chart.setPeriod(PriceChart.Period.ALL);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(table), chart);
        split.setResizeWeight(0.4);
        split.setBorder(null);

        refreshBtn.addActionListener(e -> refresh());
        addComponentListener(new ComponentAdapter() {
            @Override public void componentShown(ComponentEvent e) { refresh(); }
        });

        add(summary, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);
        add(refreshBtn, BorderLayout.SOUTH);
    }

    void refresh() {
        refreshBtn.setEnabled(false);
        Map<String, Double> live = ctx.stocks().latestPrices();
        UiTasks.run(() -> new Data(ctx.portfolio().viewPortfolioWithPnL(ctx.userId(), live),
                                   new PortfolioAnalytics(ctx.portfolio(), ctx.stocks()).report(ctx.userId())),
            data -> { refreshBtn.setEnabled(true); show(data); },
            err -> { refreshBtn.setEnabled(true); Ui.showError(ctx.frame(), "Could not load portfolio: " + err.getMessage()); });
    }

    private void show(Data data) {
        model.setRowCount(0);
        if (data.holdings().isEmpty()) model.addRow(new Object[]{"No holdings yet.", "", "", "", "", ""});
        for (PortfolioEntry en : data.holdings()) {
            model.addRow(new Object[]{en.stockName, en.quantity,
                Money.format(en.avgPrice), Money.format(en.marketValue), signed(en.unrealizedPnL),
                String.format("%+.1f%%", en.pnlPercent)});
        }

        PortfolioAnalytics.Summary s = data.report().summary();
        summary.setText(String.format("<html>Cash <b>%s</b> &nbsp;·&nbsp; Holdings <b>%s</b> &nbsp;·&nbsp; Total <b>%s</b>"
                + " &nbsp;·&nbsp; Return %s (%+.2f%%) &nbsp;·&nbsp; Realised %s &nbsp;·&nbsp; Unrealised %s</html>",
            Money.format(s.cash()), Money.format(s.holdingsValue()), Money.format(s.total()),
            coloured(s.totalReturn()), s.totalReturnPercent(), coloured(s.realizedPnL()), coloured(s.unrealizedPnL())));

        List<PricePoint> values = data.report().history().stream()
            .map(v -> new PricePoint(v.date(), v.total().doubleValue())).toList();
        chart.setSeries("Portfolio value", values, "Your portfolio's value over time appears after your first trade.");
    }

    static String signed(BigDecimal v) {
        return String.format("%s$%,.2f", v.signum() >= 0 ? "+" : "-", v.abs());
    }

    private static String coloured(BigDecimal v) {
        Color c = v.signum() < 0 ? Ui.LOSS : Ui.GAIN;
        return String.format("<font color='#%02x%02x%02x'><b>%s</b></font>", c.getRed(), c.getGreen(), c.getBlue(), signed(v));
    }
}
