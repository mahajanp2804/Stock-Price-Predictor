package com.capstone.ui;

import com.capstone.data.StockDataModule;
import com.capstone.db.DBConnection;
import com.capstone.db.PortfolioManager;
import com.capstone.model.Money;
import com.capstone.model.PortfolioEntry;
import com.capstone.model.Stock;
import com.capstone.model.Transaction;
import com.capstone.prediction.PredictionModule;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.io.File;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

/**
 * Main window. Every DB / network call runs through UiTasks so the UI never freezes.
 * Live prices refresh every minute and again right before each trade.
 */
public class StockTradingGUI extends JFrame {

    private static final Logger log = LoggerFactory.getLogger(StockTradingGUI.class);
    private static final int QUOTE_REFRESH_MS = 60_000;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter TXN_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final StockDataModule  stockDataModule;
    private final PortfolioManager portfolioManager;
    private final int    userId;
    private final String username;

    private JLabel      statusBar;
    private JTabbedPane tabbedPane;
    private boolean     darkMode = false;

    private DefaultTableModel stocksModel;
    private String  balanceText = "…";
    private Instant pricesUpdatedAt;
    private boolean quoteRefreshInFlight;
    private javax.swing.Timer quoteTimer;

    public StockTradingGUI(int userId, String username) {
        this.userId          = userId;
        this.username        = username;
        this.stockDataModule  = new StockDataModule();
        this.portfolioManager = new PortfolioManager();

        setTitle("Stock Trading App — " + username);
        setSize(820, 620);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        setJMenuBar(buildMenuBar());

        statusBar = new JLabel();
        statusBar.setFont(new Font("SansSerif", Font.PLAIN, 11));
        statusBar.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 0));
        add(statusBar, BorderLayout.SOUTH);
        refreshStatusBar();

        JPanel loadingPanel = buildLoadingPanel();
        add(loadingPanel, BorderLayout.CENTER);

        UiTasks.run(() -> { stockDataModule.loadAll(); return null; },
            ignored -> {
                remove(loadingPanel);
                tabbedPane = buildTabs();
                add(tabbedPane, BorderLayout.CENTER);
                revalidate(); repaint();
                pricesUpdatedAt = Instant.now();
                updateStatusText();
                quoteTimer = new javax.swing.Timer(QUOTE_REFRESH_MS, e -> refreshQuotes());
                quoteTimer.start();
            },
            err -> showError("Could not load stock data: " + err.getMessage()));

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
            File file = chooseSaveFile("portfolio.csv"); if (file == null) return;
            Map<String, Double> live = stockDataModule.latestPrices();
            UiTasks.run(() -> { CsvExporter.writePortfolio(file, portfolioManager.viewPortfolioWithPnL(userId, live)); return file; },
                this::showExported, err -> showError("Export failed: " + err.getMessage()));
        });
        exportTxnsItem.addActionListener(e -> {
            File file = chooseSaveFile("transactions.csv"); if (file == null) return;
            UiTasks.run(() -> { CsvExporter.writeTransactions(file, portfolioManager.viewTransactions(userId)); return file; },
                this::showExported, err -> showError("Export failed: " + err.getMessage()));
        });
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

    private File chooseSaveFile(String defaultName) {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(defaultName));
        return chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION ? chooser.getSelectedFile() : null;
    }

    private void showExported(File file) {
        JOptionPane.showMessageDialog(this, "Exported to:\n" + file.getAbsolutePath(), "Export Successful", JOptionPane.INFORMATION_MESSAGE);
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
        tp.addTab("Buy",                   createTradePanel(true));
        tp.addTab("Sell",                  createTradePanel(false));
        tp.addTab("Portfolio",             createPortfolioPanel());
        tp.addTab("Transactions",          createTransactionsPanel());
        tp.addTab("Predict Prices",        createFuturePricesPanel());
        tp.addTab("Predict Trend",         createTrendPanel());
        return tp;
    }

    // ----------------------------------------------------------------
    // View Stocks — JTable, refreshed with each quote update
    // ----------------------------------------------------------------

    private JPanel createViewStocksPanel() {
        String[] cols = {"Stock", "Latest Price", "30-Day Change", "Price As Of"};
        stocksModel = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        fillStocksTable();
        JTable table = new JTable(stocksModel);
        table.setRowHeight(24);
        table.getTableHeader().setReorderingAllowed(false);
        table.getColumnModel().getColumn(2).setCellRenderer(pnlRenderer());
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    private void fillStocksTable() {
        if (stocksModel == null) return;
        stocksModel.setRowCount(0);
        for (String name : stockDataModule.getAllStockNames()) {
            Stock stock = stockDataModule.getStockByName(name);
            if (stock == null) continue;
            double latest = stock.getLatestPrice();
            double first  = stock.getPriceHistory().get(0);
            double pct    = first == 0 ? 0 : (latest - first) / first * 100;
            Instant asOf  = stock.getLiveUpdatedAt();
            stocksModel.addRow(new Object[]{name, String.format("$%.2f", latest), String.format("%+.2f%%", pct),
                asOf == null ? "Last close" : "Live " + TIME.format(asOf)});
        }
    }

    // ----------------------------------------------------------------
    // Buy / Sell — fetch a fresh quote, then trade at it
    // ----------------------------------------------------------------

    private JPanel createTradePanel(boolean isBuy) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(20, 40, 20, 40));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(7, 7, 7, 7); gc.fill = GridBagConstraints.HORIZONTAL;

        JComboBox<String> stockCombo = buildStockCombo();
        JTextField qtyField = new JTextField(14);
        JButton tradeButton = new JButton(isBuy ? "Buy" : "Sell");
        JLabel resultLabel = new JLabel(" ");
        resultLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));

        gc.gridx=0;gc.gridy=0; panel.add(new JLabel("Stock:"),gc);
        gc.gridx=1; panel.add(stockCombo,gc);
        gc.gridx=0;gc.gridy=1; panel.add(new JLabel("Quantity:"),gc);
        gc.gridx=1; panel.add(qtyField,gc);
        gc.gridx=1;gc.gridy=2; panel.add(tradeButton,gc);
        gc.gridx=0;gc.gridy=3;gc.gridwidth=2; panel.add(resultLabel,gc);

        tradeButton.addActionListener(e -> {
            String name = (String) stockCombo.getSelectedItem(); if (name == null) return;
            int qty;
            try { qty = Integer.parseInt(qtyField.getText().trim()); }
            catch (NumberFormatException ex) { showError("Please enter a valid whole number for quantity."); return; }

            tradeButton.setEnabled(false);
            resultLabel.setText("Getting latest price…");
            UiTasks.run(() -> {
                    boolean fresh = stockDataModule.refreshQuote(name);
                    Stock stock = stockDataModule.getStockByName(name);
                    if (stock == null) return "Stock data not available for " + name;
                    double price = stock.getLatestPrice();
                    String msg = isBuy ? portfolioManager.buyStock(userId, name, qty, price)
                                       : portfolioManager.sellStock(userId, name, qty, price);
                    return fresh ? msg : msg + "<br><i>Live quote unavailable — used last known price.</i>";
                },
                msg -> {
                    resultLabel.setText("<html>" + msg + "</html>");
                    tradeButton.setEnabled(true);
                    fillStocksTable();
                    refreshStatusBar();
                },
                err -> {
                    resultLabel.setText(" ");
                    tradeButton.setEnabled(true);
                    showError((isBuy ? "Buy" : "Sell") + " failed: " + err.getMessage());
                });
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
            refreshBtn.setEnabled(false);
            Map<String, Double> live = stockDataModule.latestPrices();
            UiTasks.run(() -> portfolioManager.viewPortfolioWithPnL(userId, live),
                entries -> {
                    refreshBtn.setEnabled(true);
                    model.setRowCount(0);
                    if (entries.isEmpty()) { model.addRow(new Object[]{"No holdings yet.","","","","",""}); return; }
                    for (PortfolioEntry en : entries) {
                        model.addRow(new Object[]{en.stockName, en.quantity,
                            String.format("$%.2f", en.avgPrice), String.format("$%.2f", en.marketValue),
                            String.format("%s$%.2f", en.unrealizedPnL.signum() >= 0 ? "+" : "", en.unrealizedPnL),
                            String.format("%+.1f%%", en.pnlPercent)});
                    }
                },
                err -> { refreshBtn.setEnabled(true); showError("Could not load portfolio: " + err.getMessage()); });
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
            refreshBtn.setEnabled(false);
            UiTasks.run(() -> portfolioManager.viewTransactions(userId),
                txns -> {
                    refreshBtn.setEnabled(true);
                    StringBuilder sb = new StringBuilder();
                    for (Transaction t : txns) {
                        sb.append(String.format("[%s] %-4s  %-10s  x%d @ $%.2f  =  $%.2f%n",
                            TXN_TIME.format(t.timestamp()), t.type(), t.stockName(), t.quantity(), t.price(), t.total()));
                    }
                    area.setText(txns.isEmpty() ? "No transactions yet." : sb.toString());
                },
                err -> { refreshBtn.setEnabled(true); showError("Could not load transactions: " + err.getMessage()); });
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
                Stock stock = stockDataModule.getStockByName(name);
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
                sb.append("\n⚠ EDUCATIONAL USE ONLY. Not for real investment decisions.");
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
            Stock stock = stockDataModule.getStockByName(name);
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
        return new JComboBox<>(stockDataModule.getAllStockNames().toArray(new String[0]));
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

    /** Background quote refresh, driven by the timer; skips if one is already running. */
    private void refreshQuotes() {
        if (quoteRefreshInFlight) return;
        quoteRefreshInFlight = true;
        UiTasks.run(stockDataModule::refreshQuotes,
            updated -> {
                quoteRefreshInFlight = false;
                if (updated > 0) pricesUpdatedAt = Instant.now();
                fillStocksTable();
                updateStatusText();
            },
            err -> { quoteRefreshInFlight = false; log.warn("Quote refresh failed", err); });
    }

    private void refreshStatusBar() {
        UiTasks.run(() -> portfolioManager.getUserBalance(userId),
            balance -> { balanceText = Money.format(balance); updateStatusText(); },
            err -> { balanceText = "unavailable (database offline?)"; updateStatusText(); });
    }

    private void updateStatusText() {
        String prices = pricesUpdatedAt == null ? "loading…" : "updated " + TIME.format(pricesUpdatedAt);
        statusBar.setText(String.format("  Logged in as: %s   |   Balance: %s   |   Prices %s",
            username, balanceText, prices));
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
