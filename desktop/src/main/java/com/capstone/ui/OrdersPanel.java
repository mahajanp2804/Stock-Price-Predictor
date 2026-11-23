package com.capstone.ui;

import com.capstone.db.OrderManager;
import com.capstone.model.Money;
import com.capstone.model.Order;
import com.capstone.model.Stock;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * "Orders" tab: place limit and stop orders, see open and recent orders, cancel open ones.
 * Open orders are checked against every quote refresh (StockTradingGUI) and right after placing.
 */
class OrdersPanel extends JPanel {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final AppContext ctx;
    private final DefaultTableModel model = Ui.readOnlyModel("#", "Symbol", "Side", "Type", "Qty", "Trigger", "Status", "Fill", "Placed", "Note");
    private final JTable table = Ui.table(model);
    private final JLabel status = new JLabel(" ");

    OrdersPanel(AppContext ctx) {
        super(new BorderLayout(0, 6));
        this.ctx = ctx;
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JComboBox<String> stockCombo = ctx.combos().create();
        JComboBox<Order.Side> sideCombo = new JComboBox<>(Order.Side.values());
        JComboBox<Order.Type> typeCombo = new JComboBox<>(Order.Type.values());
        JTextField qtyField = new JTextField(5);
        JTextField triggerField = new JTextField(7);
        JButton placeBtn = new JButton("Place order");
        JLabel explain = new JLabel(" ");
        explain.setFont(explain.getFont().deriveFont(Font.ITALIC, 11f));
        Runnable updateExplain = () -> explain.setText(describe((Order.Side) sideCombo.getSelectedItem(), (Order.Type) typeCombo.getSelectedItem()));
        sideCombo.addActionListener(e -> updateExplain.run());
        typeCombo.addActionListener(e -> updateExplain.run());
        updateExplain.run();

        JPanel form = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        form.add(new JLabel("Stock:")); form.add(stockCombo);
        form.add(sideCombo); form.add(typeCombo);
        form.add(new JLabel("Qty:")); form.add(qtyField);
        form.add(new JLabel("Trigger $:")); form.add(triggerField);
        form.add(placeBtn);
        JPanel top = new JPanel(new BorderLayout());
        top.add(form, BorderLayout.NORTH);
        top.add(explain, BorderLayout.CENTER);
        top.add(status, BorderLayout.SOUTH);

        table.getColumnModel().getColumn(0).setMaxWidth(40);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JButton cancelBtn = new JButton("Cancel selected order");
        JButton refreshBtn = new JButton("Refresh");
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        bottom.add(cancelBtn); bottom.add(refreshBtn);

        add(top, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        placeBtn.addActionListener(e -> {
            String symbol = (String) stockCombo.getSelectedItem(); if (symbol == null) return;
            int qty; double trigger;
            try { qty = Integer.parseInt(qtyField.getText().trim()); trigger = Double.parseDouble(triggerField.getText().trim()); }
            catch (NumberFormatException ex) { Ui.showError(ctx.frame(), "Enter a whole-number quantity and a trigger price."); return; }
            Order.Side side = (Order.Side) sideCombo.getSelectedItem();
            Order.Type type = (Order.Type) typeCombo.getSelectedItem();
            placeBtn.setEnabled(false);
            UiTasks.run(() -> {
                    Order o = ctx.orders().place(ctx.userId(), symbol, side, type, qty, trigger);
                    // Check straight away against the current price
                    Stock stock = ctx.stocks().getStock(symbol);
                    List<OrderManager.Outcome> now = stock == null ? List.of()
                        : ctx.orders().checkAndFill(Map.of(symbol, stock.getLatestPrice()));
                    return now.stream().filter(x -> x.orderId() == o.id()).findFirst()
                        .map(x -> "Order #" + o.id() + " triggered at once: " + x.message())
                        .orElse("Order #" + o.id() + " placed. It's checked each time prices refresh.");
                },
                msg -> { placeBtn.setEnabled(true); status.setText(msg); refresh(); ctx.afterTrade().run(); },
                err -> {
                    placeBtn.setEnabled(true);
                    Ui.showError(ctx.frame(), err instanceof IllegalArgumentException ? err.getMessage() : "Could not place order: " + err.getMessage());
                });
        });

        cancelBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) { Ui.showError(ctx.frame(), "Select an open order to cancel."); return; }
            int id = (Integer) model.getValueAt(row, 0);
            UiTasks.run(() -> ctx.orders().cancel(ctx.userId(), id),
                ok -> { status.setText(ok ? "Order #" + id + " cancelled." : "Order #" + id + " is no longer open."); refresh(); },
                err -> Ui.showError(ctx.frame(), "Could not cancel: " + err.getMessage()));
        });
        refreshBtn.addActionListener(e -> refresh());
        addComponentListener(new ComponentAdapter() {
            @Override public void componentShown(ComponentEvent e) { refresh(); }
        });
    }

    static String describe(Order.Side side, Order.Type type) {
        boolean buy = side == Order.Side.BUY;
        return type == Order.Type.LIMIT
            ? (buy ? "Limit buy: buys when the price falls to or below the trigger." : "Limit sell: sells when the price rises to or above the trigger.")
            : (buy ? "Stop buy: buys when the price rises to or above the trigger." : "Stop sell (stop-loss): sells when the price falls to or below the trigger.");
    }

    /** Reloads the order list. Call on the UI thread. */
    void refresh() {
        UiTasks.run(() -> ctx.orders().list(ctx.userId()),
            orders -> {
                model.setRowCount(0);
                for (Order o : orders) {
                    model.addRow(new Object[]{o.id(), o.symbol(), o.side(), o.type(), o.quantity(), Money.format(o.triggerPrice()),
                        o.status(), o.fillPrice() == null ? "" : Money.format(o.fillPrice()), TIME.format(o.createdAt()),
                        o.message() == null ? "" : o.message()});
                }
            },
            err -> status.setText("Could not load orders: " + err.getMessage()));
    }
}
