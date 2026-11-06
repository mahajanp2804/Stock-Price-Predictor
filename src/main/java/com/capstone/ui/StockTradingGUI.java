package com.capstone.ui;

import com.capstone.data.StockDataModule;
import com.capstone.db.DBConnection;
import com.capstone.db.PortfolioManager;
import com.capstone.model.Money;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.time.Instant;
import java.util.Map;

/**
 * Main window: menu, status bar, tabs and the live-price timer. Each tab is its own
 * class (ViewStocksPanel, TradePanel, …); every DB / network call goes through UiTasks
 * so the UI never freezes. Live prices refresh every minute and again before each trade.
 */
public class StockTradingGUI extends JFrame {

    private static final Logger log = LoggerFactory.getLogger(StockTradingGUI.class);
    private static final int QUOTE_REFRESH_MS = 60_000;

    private final StockDataModule  stockDataModule;
    private final PortfolioManager portfolioManager;
    private final int    userId;
    private final String username;

    private final JLabel statusBar = new JLabel();
    private boolean darkMode = false;

    private ViewStocksPanel viewStocksPanel;
    private String  balanceText = "…";
    private Instant pricesUpdatedAt;
    private boolean quoteRefreshInFlight;

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

        statusBar.setFont(new Font("SansSerif", Font.PLAIN, 11));
        statusBar.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 0));
        add(statusBar, BorderLayout.SOUTH);
        refreshStatusBar();

        JPanel loadingPanel = buildLoadingPanel();
        add(loadingPanel, BorderLayout.CENTER);

        UiTasks.run(() -> { stockDataModule.loadAll(); return null; },
            ignored -> {
                remove(loadingPanel);
                add(buildTabs(), BorderLayout.CENTER);
                revalidate(); repaint();
                pricesUpdatedAt = Instant.now();
                updateStatusText();
                new Timer(QUOTE_REFRESH_MS, e -> refreshQuotes()).start();
            },
            err -> Ui.showError(this, "Could not load stock data: " + err.getMessage()));

        Runtime.getRuntime().addShutdownHook(new Thread(DBConnection::shutdown));
    }

    // ----------------------------------------------------------------
    // Tabs
    // ----------------------------------------------------------------

    private JTabbedPane buildTabs() {
        AppContext ctx = new AppContext(stockDataModule, portfolioManager, userId, this, this::afterTrade);
        viewStocksPanel = new ViewStocksPanel(ctx);
        JTabbedPane tp = new JTabbedPane();
        tp.addTab("View Stocks",    viewStocksPanel);
        tp.addTab("Buy",            new TradePanel(ctx, true));
        tp.addTab("Sell",           new TradePanel(ctx, false));
        tp.addTab("Portfolio",      new PortfolioPanel(ctx));
        tp.addTab("Transactions",   new TransactionsPanel(ctx));
        tp.addTab("Predict Prices", new PredictPricesPanel(ctx));
        tp.addTab("Predict Trend",  new TrendPanel(ctx));
        return tp;
    }

    /** A trade fetched a fresh quote and changed the balance. */
    private void afterTrade() {
        viewStocksPanel.refresh();
        refreshStatusBar();
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
                this::showExported, err -> Ui.showError(this, "Export failed: " + err.getMessage()));
        });
        exportTxnsItem.addActionListener(e -> {
            File file = chooseSaveFile("transactions.csv"); if (file == null) return;
            UiTasks.run(() -> { CsvExporter.writeTransactions(file, portfolioManager.viewTransactions(userId)); return file; },
                this::showExported, err -> Ui.showError(this, "Export failed: " + err.getMessage()));
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
    // Live prices and status bar
    // ----------------------------------------------------------------

    /** Background quote refresh, driven by the timer; skips if one is already running. */
    private void refreshQuotes() {
        if (quoteRefreshInFlight) return;
        quoteRefreshInFlight = true;
        UiTasks.run(stockDataModule::refreshQuotes,
            updated -> {
                quoteRefreshInFlight = false;
                if (updated > 0) pricesUpdatedAt = Instant.now();
                viewStocksPanel.refresh();
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
        String prices = pricesUpdatedAt == null ? "loading…" : "updated " + ViewStocksPanel.TIME.format(pricesUpdatedAt);
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
