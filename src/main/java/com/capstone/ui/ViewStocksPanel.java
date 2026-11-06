package com.capstone.ui;

import com.capstone.data.StockDataModule;
import com.capstone.model.Stock;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** "View Stocks" tab: latest price, 30-day change and how fresh each price is. */
class ViewStocksPanel extends JPanel {

    static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final StockDataModule stocks;
    private final DefaultTableModel model = Ui.readOnlyModel("Stock", "Latest Price", "30-Day Change", "Price As Of");

    ViewStocksPanel(AppContext ctx) {
        super(new BorderLayout());
        this.stocks = ctx.stocks();
        JTable table = Ui.table(model);
        table.getColumnModel().getColumn(2).setCellRenderer(Ui.pnlRenderer());
        add(new JScrollPane(table), BorderLayout.CENTER);
        refresh();
    }

    /** Re-reads prices from the data module (no I/O). Call on the UI thread. */
    void refresh() {
        model.setRowCount(0);
        for (String name : stocks.getAllStockNames()) {
            Stock stock = stocks.getStockByName(name);
            if (stock == null) continue;
            double latest = stock.getLatestPrice();
            List<Double> history = stock.getPriceHistory();
            double first  = history.get(0);
            // Only one price means history providers failed and this is the DB cache
            String change = history.size() < 2 || first == 0 ? "n/a" : String.format("%+.2f%%", (latest - first) / first * 100);
            Instant asOf  = stock.getLiveUpdatedAt();
            model.addRow(new Object[]{name, String.format("$%.2f", latest), change,
                asOf == null ? "Last close" : "Live " + TIME.format(asOf)});
        }
    }
}
