package com.capstone.ui;

import com.capstone.data.SymbolSearch;
import com.capstone.db.WatchlistManager;
import com.capstone.model.WatchItem;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/** Search US stocks / ETFs by ticker or name and add one to the watchlist. */
class AddStockDialog extends JDialog {

    private final AppContext ctx;
    private final Runnable onAdded;
    private final SymbolSearch search = new SymbolSearch();

    private final JTextField queryField = new JTextField(22);
    private final JButton searchBtn = new JButton("Search");
    private final DefaultListModel<WatchItem> results = new DefaultListModel<>();
    private final JList<WatchItem> resultList = new JList<>(results);
    private final JButton addBtn = new JButton("Add to watchlist");
    private final JLabel status = new JLabel(" ");

    /** @param onAdded run on the UI thread after a stock has been added and loaded */
    AddStockDialog(AppContext ctx, Runnable onAdded) {
        super(ctx.frame(), "Add stock", true);
        this.ctx = ctx;
        this.onAdded = onAdded;
        setSize(460, 400);
        setLocationRelativeTo(ctx.frame());

        resultList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean sel, boolean foc) {
                WatchItem w = (WatchItem) v;
                return super.getListCellRendererComponent(l, String.format("%-7s %s", w.symbol(), w.companyName()), i, sel, foc);
            }
        });
        resultList.setFont(new Font("Monospaced", Font.PLAIN, 12));
        resultList.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { if (e.getClickCount() == 2) addSelected(); }
        });
        resultList.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "add");
        resultList.getActionMap().put("add", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { addSelected(); }
        });

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        top.add(new JLabel("Ticker or company:"));
        top.add(queryField);
        top.add(searchBtn);

        JPanel bottom = new JPanel(new BorderLayout(8, 0));
        bottom.setBorder(BorderFactory.createEmptyBorder(6, 8, 8, 8));
        bottom.add(status, BorderLayout.CENTER);
        bottom.add(addBtn, BorderLayout.EAST);

        JPanel root = new JPanel(new BorderLayout());
        root.add(top, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(resultList);
        scroll.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8), scroll.getBorder()));
        root.add(scroll, BorderLayout.CENTER);
        root.add(bottom, BorderLayout.SOUTH);
        setContentPane(root);

        queryField.addActionListener(e -> runSearch());
        searchBtn.addActionListener(e -> runSearch());
        addBtn.addActionListener(e -> addSelected());
        getRootPane().setDefaultButton(searchBtn);

        if (!search.isConfigured()) {
            setBusy(true);
            queryField.setEnabled(false);
            status.setText("Search needs a Finnhub API key in config.properties (finnhub.key).");
        }
    }

    private void runSearch() {
        String query = queryField.getText().trim();
        if (query.isEmpty()) return;
        setBusy(true);
        status.setText("Searching…");
        results.clear();
        UiTasks.run(() -> search.search(query),
            found -> {
                setBusy(false);
                found.forEach(results::addElement);
                status.setText(found.isEmpty() ? "No US stocks or ETFs match \"" + query + "\"." : found.size() + " result(s). Double-click or select and Add.");
                if (!found.isEmpty()) { resultList.setSelectedIndex(0); resultList.requestFocusInWindow(); }
            },
            err -> { setBusy(false); status.setText("Search failed: " + err.getMessage()); });
    }

    /** Adds to the watchlist, then loads history and a live quote; undoes the add if no prices exist. */
    private void addSelected() {
        WatchItem item = resultList.getSelectedValue();
        if (item == null) { status.setText("Select a result first."); return; }
        if (ctx.stocks().getStock(item.symbol()) != null) { status.setText(item.symbol() + " is already on your watchlist."); return; }
        setBusy(true);
        status.setText("Adding " + item.symbol() + " and loading its prices…");
        UiTasks.run(() -> {
                WatchlistManager.AddResult result = ctx.watchlist().add(ctx.userId(), item.symbol(), item.companyName());
                if (result == WatchlistManager.AddResult.FULL) return "Your watchlist is full (" + WatchlistManager.MAX_STOCKS + " stocks). Remove one first.";
                if (!ctx.stocks().load(item.symbol(), item.companyName())) {
                    ctx.watchlist().remove(ctx.userId(), item.symbol());
                    return "No price data is available for " + item.symbol() + ", so it wasn't added.";
                }
                ctx.stocks().refreshQuote(item.symbol());
                return null;
            },
            problem -> {
                setBusy(false);
                if (problem != null) { status.setText(problem); return; }
                onAdded.run();
                dispose();
            },
            err -> { setBusy(false); status.setText("Could not add " + item.symbol() + ": " + err.getMessage()); });
    }

    private void setBusy(boolean busy) {
        searchBtn.setEnabled(!busy);
        addBtn.setEnabled(!busy);
    }
}
