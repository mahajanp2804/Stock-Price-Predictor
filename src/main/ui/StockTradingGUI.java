package com.capstone.ui;

import com.capstone.data.StockDataModule;
import com.capstone.db.DBConnection;
import com.capstone.db.PortfolioManager;
import com.capstone.model.PortfolioEntry;
import com.capstone.model.Stock;
import com.capstone.prediction.PredictionModule;
import com.capstone.prediction.Utils;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Phase 4:
 *  - Proper package: com.capstone.ui
 *  - Dark / light theme toggle via FlatLaf (File menu)
 *  - CSV export for portfolio and transactions (File menu)
 *  - All cross-package imports resolved
 */
public class StockTradingGUI extends JFrame {

    private final StockDataModule  stockDataModule;
    private final PortfolioManager portfolioManager;
    private final CsvExporter      csvExporter;
    private final int    userId;
    private final String username;

    private JLabel      statusBar;
    private JTabbedPane tabbedPane;
    private boolean     darkMode = false;

    public StockTradingGUI(int userId, String username) {
        this.userId          = userId;
        this.username        = username;
        this.stockDataModule  = new StockDataModule();
        this.portfolioManager = new PortfolioManager();
        this.csvExporter      = new CsvExporter(portfolioManager, userId);

        setTitle("Stock Trading App — " + username);
        setSize(820, 620);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        setJMenuBar(buildMenuBar());

        statusBar = new JLabel();
        statusBar.setFont(new Font("SansSerif", Font.PLAIN, 11));
        statusBar.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 0));
        refreshStatusBar();
        add(statusBar, BorderLayout.SOUTH);

        JPanel loadingPanel = buildLoadingPanel();
        add(loadingPanel, BorderLayout.CENTER);

        stockDataModule.populateStockDataAsync(() -> {
            remove(loadingPanel);
            tabbedPane = buildTabs();
            add(tabbedPane, BorderLayout.CENTER);
            revalidate(); repaint();
        });

        Runtime.getRuntime().addShutdownHook(new Thread(DBConnection::shutdown));
    }

    // ----------------------------------------------------------------
    // Menu bar — File (CSV export, theme toggle)
    // ----------------------------------------------------------------

    private JMenuBar buildMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");

        JMenuItem exportPortfolioItem = new JMenuItem("Export Portfolio to CSV…");
        JMenuItem exportTxnsItem      = new JMenuItem("Export Transactions to CSV…");
        JMenuItem themeItem           = new JMenuItem("Toggle Dark / Light Theme");
        JMenuItem exitItem            = new JMenuItem("Exit");

        exportPortfolioItem.addActionListener(e -> {
            Map<String, Double> live = buildLivePriceMap();
            csvExporter.exportPortfolio(this, live);
        });
        exportTxnsItem.addActionListener(e -> csvExporter.exportTransactions(this));
        themeItem.addActionListener(e -> toggleTheme());
        exitItem.addActionListener(e -> System.exit(0));

        fileMenu.add(exportPortfolioItem);
        fileMenu.add(exportTxnsItem);
        fileMenu.addSeparator();
        fileMenu.add(themeItem);
        fileMenu.addSeparator();
        fileMenu.add(exitItem);
        menuBar.add(fileMenu);
        return menuBar;
    }

    // ----------------------------------------------------------------
    // Theme toggle (FlatLaf)
    // ----------------------------------------------------------------

    private void toggleTheme() {
        try {
            if (darkMode) {
                FlatLightLaf.setup();
            } else {
                FlatDarkLaf.setup();
            }
            darkMode = !darkMode;
            SwingUtilities.updateComponentTreeUI(this);
            pack();
            setSize(820, 620);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                "Theme switch failed: " + e.getMessage(),
                "Theme Error", JOptionPane.WARNING_MESSAGE);
        }
    }

    // ----------------------------------------------------------------
    // Loading panel
    // ----------------------------------------------------------------

    private JPanel buildLoadingPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        JLabel lbl = new JLabel("Loading stock data…", SwingConstants.CENTER);
        lbl.setFont(new Font("SansSerif", Font.ITALIC, 14));
        JProgressBar bar = new JProgressBar();
        bar.setIndeterminate(true);
        bar.setPreferredSize(new Dimension(260, 14));
        JPanel inner = new JPanel();
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
        lbl.setAlignmentX(Component.CENTER_ALIGNMENT);
        bar.setAlignmentX(Component.CENTER_ALIGNMENT);
        inner.add(lbl); inner.add(Box.createVerticalStrut(12)); inner.add(bar);
        p.add(inner);
        return p;
    }

    // ----------------------------------------------------------------
    // Tabs
    // ----------------------------------------------------------------

    private JTabbedPane buildTabs() {
        JTabbedPane tp = new JTabbedPane();
        tp.addTab("View Stocks",           createViewStocksPanel());
        tp.addTab("Buy",                   createBuyStocksPanel());
        tp.addTab("Sell",                  createSellStocksPanel());
        tp.addTab("Portfolio",             createPortfolioPanel());
        tp.addTab("Transactions",          createTransactionsPanel());
        tp.addTab("Predict Prices",        createFuturePricesPanel());
        tp.addTab("Predict Trend",         createTrendPanel());
        return tp;
    }

    // ----------------------------------------------------------------
    // View Stocks — JTable
    // ----------------------------------------------------------------

    private JPanel createViewStocksPanel() {
        String[] cols = {"Stock", "Latest Price", "30-Day Change"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        List<String> names = new ArrayList<>(stockDataModule.getAllStockNames());
        Collections.sort(names);
        for (String s : names) {
            Stock stock = stockDataModule.getStockByName(s);
            if (stock == null) continue;
            List<Double> hist = stock.getPriceHistory();
            double latest = stock.getLatestPrice();
            double first  = hist.get(0);
            double pct    = first == 0 ? 0 : (latest - first) / first * 100;
            model.addRow(new Object[]{Utils.capitalize(s), String.format("$%.2f", latest), String.format("%+.2f%%", pct)});
        }
        JTable table = new JTable(model);
        table.setRowHeight(24);
        table.getTableHeader().setReorderingAllowed(false);
        table.getColumnModel().getColumn(2).setCellRenderer(pnlRenderer());
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    // ----------------------------------------------------------------
    // Buy
    // ----------------------------------------------------------------

    private JPanel createBuyStocksPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(20, 40, 20, 40));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(7, 7, 7, 7); gc.fill = GridBagConstraints.HORIZONTAL;

        JComboBox<String> stockCombo = buildStockCombo();
        JTextField qtyField = new JTextField(14);
        JButton buyButton = new JButton("Buy");
        JLabel resultLabel = new JLabel(" ");
        resultLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));

        gc.gridx=0;gc.gridy=0; panel.add(new JLabel("Stock:"),gc);
        gc.gridx=1; panel.add(stockCombo,gc);
        gc.gridx=0;gc.gridy=1; panel.add(new JLabel("Quantity:"),gc);
        gc.gridx=1; panel.add(qtyField,gc);
        gc.gridx=1;gc.gridy=2; panel.add(buyButton,gc);
        gc.gridx=0;gc.gridy=3;gc.gridwidth=2; panel.add(resultLabel,gc);

        buyButton.addActionListener(e -> {
            String name = (String) stockCombo.getSelectedItem(); if (name == null) return;
            try {
                int qty = Integer.parseInt(qtyField.getText().trim());
                Stock stock = stockDataModule.getStockByName(name.toLowerCase());
                if (stock == null) { showError("Stock data not available for " + name); return; }
                String msg = portfolioManager.buyStock(userId, name, qty, stock.getLatestPrice());
                resultLabel.setText("<html>" + msg + "</html>"); refreshStatusBar();
            } catch (NumberFormatException ex) { showError("Please enter a valid whole number for quantity."); }
        });
        return panel;
    }

    // ----------------------------------------------------------------
    // Sell
    // ----------------------------------------------------------------

    private JPanel createSellStocksPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(20, 40, 20, 40));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(7, 7, 7, 7); gc.fill = GridBagConstraints.HORIZONTAL;

        JComboBox<String> stockCombo = buildStockCombo();
        JTextField qtyField = new JTextField(14);
        JButton sellButton = new JButton("Sell");
        JLabel resultLabel = new JLabel(" ");
        resultLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));

        gc.gridx=0;gc.gridy=0; panel.add(new JLabel("Stock:"),gc);
        gc.gridx=1; panel.add(stockCombo,gc);
        gc.gridx=0;gc.gridy=1; panel.add(new JLabel("Quantity:"),gc);
        gc.gridx=1; panel.add(qtyField,gc);
        gc.gridx=1;gc.gridy=2; panel.add(sellButton,gc);
        gc.gridx=0;gc.gridy=3;gc.gridwidth=2; panel.add(resultLabel,gc);

        sellButton.addActionListener(e -> {
            String name = (String) stockCombo.getSelectedItem(); if (name == null) return;
            try {
                int qty = Integer.parseInt(qtyField.getText().trim());
                Stock stock = stockDataModule.getStockByName(name.toLowerCase());
                if (stock == null) { showError("Stock data not available for " + name); return; }
                String msg = portfolioManager.sellStock(userId, name, qty, stock.getLatestPrice());
                resultLabel.setText("<html>" + msg + "</html>"); refreshStatusBar();
            } catch (NumberFormatException ex) { showError("Please enter a valid whole number for quantity."); }
        });
        return panel;
    }

    // ----------------------------------------------------------------
    // Portfolio — JTable with P&L, colour-coded
    // ----------------------------------------------------------------

    private JPanel createPortfolioPanel() {
        String[] cols = {"Stock","Qty","Avg Price","Market Value","Unrealised P&L","P&L %"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable table = new JTable(model);
        table.setRowHeight(24);
        table.getTableHeader().setReorderingAllowed(false);
        table.getColumnModel().getColumn(4).setCellRenderer(pnlRenderer());
        table.getColumnModel().getColumn(5).setCellRenderer(pnlRenderer());

        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> {
            model.setRowCount(0);
            Map<String, Double> live = buildLivePriceMap();
            List<PortfolioEntry> entries = portfolioManager.viewPortfolioWithPnL(userId, live);
            if (entries.isEmpty()) { model.addRow(new Object[]{"No holdings yet.","","","","",""}); }
            else for (PortfolioEntry en : entries) {
                model.addRow(new Object[]{en.stockName, en.quantity,
                    String.format("$%.2f", en.avgPrice), String.format("$%.2f", en.marketValue),
                    String.format("%s$%.2f", en.unrealizedPnL >= 0 ? "+" : "", en.unrealizedPnL),
                    String.format("%+.1f%%", en.pnlPercent)});
            }
        });

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        panel.add(refreshBtn, BorderLayout.SOUTH);
        return panel;
    }

    // ----------------------------------------------------------------
    // Transactions
    // ----------------------------------------------------------------

    private JPanel createTransactionsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setFont(new Font("Monospaced", Font.PLAIN, 12));
        area.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> {
            List<String> txns = portfolioManager.viewTransactions(userId);
            area.setText(txns.isEmpty() ? "No transactions yet." : String.join("\n", txns));
        });
        panel.add(new JScrollPane(area), BorderLayout.CENTER);
        panel.add(refreshBtn, BorderLayout.SOUTH);
        return panel;
    }

    // ----------------------------------------------------------------
    // Predict Future Prices
    // ----------------------------------------------------------------

    private JPanel createFuturePricesPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        JPanel inputRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JComboBox<String> stockCombo = buildStockCombo();
        JTextField daysField = new JTextField(5);
        JButton predictBtn = new JButton("Predict");
        inputRow.add(new JLabel("Stock:")); inputRow.add(stockCombo);
        inputRow.add(new JLabel("Days ahead:")); inputRow.add(daysField);
        inputRow.add(predictBtn);
        JTextArea resultArea = new JTextArea();
        resultArea.setEditable(false);
        resultArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        panel.add(inputRow, BorderLayout.NORTH);
        panel.add(new JScrollPane(resultArea), BorderLayout.CENTER);
        predictBtn.addActionListener(e -> {
            String name = (String) stockCombo.getSelectedItem(); if (name == null) return;
            try {
                int days = Integer.parseInt(daysField.getText().trim());
                Stock stock = stockDataModule.getStockByName(name.toLowerCase());
                if (stock == null) { showError("Stock data unavailable."); return; }
                List<Double> hist = stock.getPriceHistory();
                List<Double> prices = PredictionModule.predictFuturePrices(hist, days);
                double sma5  = PredictionModule.sma(hist, 5);
                double sma20 = PredictionModule.sma(hist, Math.min(20, hist.size()));
                double ema14 = PredictionModule.ema(hist, Math.min(14, hist.size()));
                double rsi   = PredictionModule.rsi(hist, Math.min(14, hist.size()-1));
                StringBuilder sb = new StringBuilder();
                sb.append("=== Indicators ===\n");
                sb.append(String.format("  SMA-5  : $%.2f%n", sma5));
                sb.append(String.format("  SMA-20 : $%.2f%n", sma20));
                sb.append(String.format("  EMA-14 : $%.2f%n", ema14));
                sb.append(String.format("  RSI-14 : %.1f  %s%n", rsi, rsi > 70 ? "(Overbought)" : rsi < 30 ? "(Oversold)" : "(Neutral)"));
                sb.append("\n=== Predicted Prices ===\n");
                for (int i = 0; i < prices.size(); i++) sb.append(String.format("  Day +%-3d: $%.2f%n", i+1, prices.get(i)));
                sb.append("\n\u26a0 EDUCATIONAL USE ONLY. Not for real investment decisions.");
                resultArea.setText(sb.toString());
            } catch (NumberFormatException ex) { showError("Please enter a valid whole number for days."); }
        });
        return panel;
    }

    // ----------------------------------------------------------------
    // Predict Trend
    // ----------------------------------------------------------------

    private JPanel createTrendPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(20, 40, 20, 40));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(8, 8, 8, 8); gc.fill = GridBagConstraints.HORIZONTAL;
        JComboBox<String> stockCombo = buildStockCombo();
        JButton predictBtn = new JButton("Analyse Trend");
        JLabel resultLabel = new JLabel(" ");
        resultLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        gc.gridx=0;gc.gridy=0; panel.add(new JLabel("Stock:"),gc);
        gc.gridx=1; panel.add(stockCombo,gc);
        gc.gridx=1;gc.gridy=1; panel.add(predictBtn,gc);
        gc.gridx=0;gc.gridy=2;gc.gridwidth=2; panel.add(resultLabel,gc);
        predictBtn.addActionListener(e -> {
            String name = (String) stockCombo.getSelectedItem(); if (name == null) return;
            Stock stock = stockDataModule.getStockByName(name.toLowerCase());
            if (stock == null) { showError("Stock data unavailable."); return; }
            String trend = PredictionModule.predictTrend(stock.getPriceHistory());
            resultLabel.setText(name + ": " + trend);
            resultLabel.setForeground(trend.contains("Up") ? new Color(30, 130, 30) : trend.contains("Down") ? new Color(180, 40, 40) : Color.DARK_GRAY);
        });
        return panel;
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    private JComboBox<String> buildStockCombo() {
        List<String> names = new ArrayList<>(stockDataModule.getAllStockNames());
        names.replaceAll(Utils::capitalize);
        Collections.sort(names);
        return new JComboBox<>(names.toArray(new String[0]));
    }

    private Map<String, Double> buildLivePriceMap() {
        Map<String, Double> live = new HashMap<>();
        for (String s : stockDataModule.getAllStockNames()) {
            Stock st = stockDataModule.getStockByName(s);
            if (st != null) live.put(Utils.capitalize(s), st.getLatestPrice());
        }
        return live;
    }

    private TableCellRenderer pnlRenderer() {
        return new DefaultTableCellRenderer() {
            public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int r, int c) {
                Component comp = super.getTableCellRendererComponent(t, v, sel, foc, r, c);
                String val = v == null ? "" : v.toString();
                comp.setForeground(val.startsWith("-") ? new Color(180, 40, 40) : new Color(30, 130, 30));
                setHorizontalAlignment(RIGHT);
                return comp;
            }
        };
    }

    private void showError(String msg) { JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE); }

    private void refreshStatusBar() {
        double balance = portfolioManager.getUserBalance(userId);
        statusBar.setText(String.format("  Logged in as: %s   |   Balance: %s   |   File → Export CSV / Toggle Theme",
            username, Utils.formatDollars(balance)));
    }

    // ----------------------------------------------------------------
    // Entry point — apply FlatLightLaf then show login
    // ----------------------------------------------------------------

    public static void main(String[] args) {
        try { FlatLightLaf.setup(); } catch (Exception e) { /* fall back to system L&F */ }
        SwingUtilities.invokeLater(() -> {
            LoginDialog login = new LoginDialog(null);
            login.setVisible(true);
            int uid = login.getLoggedInUserId();
            if (uid < 0) System.exit(0);
            new StockTradingGUI(uid, login.getLoggedInUsername()).setVisible(true);
        });
    }
}
