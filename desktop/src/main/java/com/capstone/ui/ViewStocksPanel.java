package com.capstone.ui;

import com.capstone.data.StockDataModule;
import com.capstone.model.Stock;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.OptionalDouble;

/**
 * "View Stocks" tab: the user's watchlist with latest price, 30-day change and how
 * fresh each price is, and a price chart of the selected stock below it.
 */
class ViewStocksPanel extends JPanel {

    static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final AppContext ctx;
    private final StockDataModule stocks;
    private final DefaultTableModel model = Ui.readOnlyModel("Symbol", "Company", "Latest Price", "30-Day Change", "Price As Of");
    private final JTable table = Ui.table(model);
    private final JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
    private final PriceChart chart = new PriceChart();

    ViewStocksPanel(AppContext ctx) {
        super(new BorderLayout());
        this.ctx = ctx;
        this.stocks = ctx.stocks();
        table.getColumnModel().getColumn(3).setCellRenderer(Ui.pnlRenderer());
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) chart.setStock(selectedSymbol() == null ? null : stocks.getStock(selectedSymbol()));
        });

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(table), chartPanel());
        split.setResizeWeight(0.35);
        split.setBorder(null);
        add(split, BorderLayout.CENTER);

        JButton addBtn = new JButton("Add stock…");
        addBtn.addActionListener(e -> new AddStockDialog(ctx, this::watchlistChanged).setVisible(true));
        JButton removeBtn = new JButton("Remove from watchlist");
        removeBtn.addActionListener(e -> removeSelected(removeBtn));
        actions.add(addBtn);
        actions.add(removeBtn);
        add(actions, BorderLayout.SOUTH);
        refresh();
    }

    private JPanel chartPanel() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        ButtonGroup group = new ButtonGroup();
        for (PriceChart.Period p : PriceChart.Period.values()) {
            JToggleButton b = new JToggleButton(p.label, p == PriceChart.Period.THREE_MONTHS);
            b.addActionListener(e -> chart.setPeriod(p));
            group.add(b);
            bar.add(b);
        }
        bar.add(Box.createHorizontalStrut(12));
        JCheckBox forecast = new JCheckBox("Show " + PriceChart.FORECAST_DAYS + "-day forecast range");
        forecast.addActionListener(e -> chart.setShowForecast(forecast.isSelected()));
        bar.add(forecast);
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(bar, BorderLayout.NORTH);
        panel.add(chart, BorderLayout.CENTER);
        return panel;
    }

    private String selectedSymbol() {
        int row = table.getSelectedRow();
        return row < 0 ? null : (String) model.getValueAt(row, 0);
    }

    /** Removes the selected stock, unless the user still holds shares of it. */
    private void removeSelected(JButton removeBtn) {
        int row = table.getSelectedRow();
        if (row < 0) { Ui.showError(ctx.frame(), "Select a stock to remove."); return; }
        String symbol = (String) model.getValueAt(row, 0);
        removeBtn.setEnabled(false);
        UiTasks.run(() -> {
                int held = ctx.portfolio().heldQuantity(ctx.userId(), symbol);
                if (held > 0) return String.format("You hold %d share(s) of %s. Sell them before removing it.", held, symbol);
                ctx.watchlist().remove(ctx.userId(), symbol);
                return null;
            },
            problem -> {
                removeBtn.setEnabled(true);
                if (problem != null) { Ui.showError(ctx.frame(), problem); return; }
                stocks.remove(symbol);
                watchlistChanged();
            },
            err -> { removeBtn.setEnabled(true); Ui.showError(ctx.frame(), "Could not remove " + symbol + ": " + err.getMessage()); });
    }

    /** Call on the UI thread after stocks are added or removed. */
    void watchlistChanged() {
        refresh();
        ctx.combos().refreshAll();
    }

    /** Re-reads prices from the data module (no I/O). Call on the UI thread. */
    void refresh() {
        String selected = selectedSymbol();
        model.setRowCount(0);
        for (String symbol : stocks.getSymbols()) {
            Stock stock = stocks.getStock(symbol);
            if (stock == null) continue;
            double latest = stock.getLatestPrice();
            // Empty when only a cached price is available (history providers failed)
            OptionalDouble pct = stock.percentChangeSince(LocalDate.now().minusDays(30));
            String change = pct.isPresent() ? String.format("%+.2f%%", pct.getAsDouble()) : "n/a";
            Instant asOf  = stock.getLiveUpdatedAt();
            model.addRow(new Object[]{symbol, stock.getName(), String.format("$%.2f", latest), change,
                asOf == null ? "Last close" : "Live " + TIME.format(asOf)});
        }
        int row = 0;
        for (int r = 0; r < model.getRowCount(); r++) if (model.getValueAt(r, 0).equals(selected)) row = r;
        if (model.getRowCount() > 0) table.setRowSelectionInterval(row, row);
        else chart.setStock(null);
    }
}
