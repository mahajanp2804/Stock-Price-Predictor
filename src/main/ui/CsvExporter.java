package com.capstone.ui;

import com.capstone.db.PortfolioManager;
import com.capstone.model.PortfolioEntry;
import javax.swing.*;
import java.io.*;
import java.util.*;

/**
 * Phase 4: Exports portfolio and transaction history to CSV files.
 * Triggered from the File menu in StockTradingGUI.
 */
public class CsvExporter {

    private final PortfolioManager portfolioManager;
    private final int userId;

    public CsvExporter(PortfolioManager portfolioManager, int userId) {
        this.portfolioManager = portfolioManager;
        this.userId           = userId;
    }

    /** Opens a save dialog and writes the user's portfolio to a CSV. */
    public void exportPortfolio(JFrame parent, Map<String, Double> livePrices) {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("portfolio.csv"));
        if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) return;

        File file = chooser.getSelectedFile();
        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            pw.println("Stock,Quantity,Avg Price,Current Price,Market Value,Unrealised P&L,P&L %");
            List<PortfolioEntry> entries = portfolioManager.viewPortfolioWithPnL(userId, livePrices);
            for (PortfolioEntry e : entries) {
                pw.printf("%s,%d,%.2f,%.2f,%.2f,%.2f,%.2f%n",
                    e.stockName, e.quantity, e.avgPrice,
                    e.currentPrice, e.marketValue,
                    e.unrealizedPnL, e.pnlPercent);
            }
            JOptionPane.showMessageDialog(parent,
                "Portfolio exported to:\n" + file.getAbsolutePath(),
                "Export Successful", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(parent,
                "Export failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Opens a save dialog and writes the user's transaction history to a CSV. */
    public void exportTransactions(JFrame parent) {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("transactions.csv"));
        if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) return;

        File file = chooser.getSelectedFile();
        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            pw.println("Timestamp,Type,Stock,Quantity,Price,Total");
            List<String> txns = portfolioManager.viewTransactions(userId);
            // Re-query as structured data for proper CSV
            for (String line : txns) {
                // lines are already formatted — write them as quoted CSV fields
                pw.println("\"" + line.replace("\"", "\"\"") + "\"");
            }
            JOptionPane.showMessageDialog(parent,
                "Transactions exported to:\n" + file.getAbsolutePath(),
                "Export Successful", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(parent,
                "Export failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
