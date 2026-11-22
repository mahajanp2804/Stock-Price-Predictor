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

/** "View Stocks" tab: latest price, 30-day change and how fresh each price is. */
class ViewStocksPanel extends JPanel {

    static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final StockDataModule stocks;
    private final DefaultTableModel model = Ui.readOnlyModel("Symbol", "Company", "Latest Price", "30-Day Change", "Price As Of");

    ViewStocksPanel(AppContext ctx) {
        super(new BorderLayout());
        this.stocks = ctx.stocks();
        JTable table = Ui.table(model);
        table.getColumnModel().getColumn(3).setCellRenderer(Ui.pnlRenderer());
        add(new JScrollPane(table), BorderLayout.CENTER);
        refresh();
    }

    /** Re-reads prices from the data module (no I/O). Call on the UI thread. */
    void refresh() {
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
    }
}
