package com.capstone.ui;

import com.capstone.model.PortfolioEntry;
import com.capstone.model.Transaction;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Writes portfolio and transaction history to CSV (one value per column, RFC 4180 quoting). */
public final class CsvExporter {

    private CsvExporter() {}

    public static void writePortfolio(File file, List<PortfolioEntry> entries) throws IOException {
        try (PrintWriter pw = new PrintWriter(file, StandardCharsets.UTF_8)) {
            pw.println("Stock,Quantity,Avg Price,Current Price,Market Value,Unrealised P&L,P&L %");
            for (PortfolioEntry e : entries) {
                pw.println(String.join(",", field(e.stockName), String.valueOf(e.quantity),
                    e.avgPrice.toPlainString(), e.currentPrice.toPlainString(), e.marketValue.toPlainString(),
                    e.unrealizedPnL.toPlainString(), String.format("%.2f", e.pnlPercent)));
            }
        }
    }

    public static void writeTransactions(File file, List<Transaction> txns) throws IOException {
        try (PrintWriter pw = new PrintWriter(file, StandardCharsets.UTF_8)) {
            pw.println("Timestamp,Type,Stock,Quantity,Price,Total");
            for (Transaction t : txns) {
                pw.println(String.join(",", t.timestamp().toString(), t.type(), field(t.stockName()),
                    String.valueOf(t.quantity()), t.price().toPlainString(), t.total().toPlainString()));
            }
        }
    }

    /** Quotes a value if it contains a comma, quote or newline. */
    static String field(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) return "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }
}
