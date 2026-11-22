package com.capstone.ui;

import com.capstone.model.PortfolioEntry;
import com.capstone.model.Transaction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CsvExporterTest {

    @TempDir Path dir;

    @Test
    @DisplayName("fields with commas or quotes are quoted")
    void quoting() {
        assertEquals("Apple", CsvExporter.field("Apple"));
        assertEquals("\"Acme, Inc\"", CsvExporter.field("Acme, Inc"));
        assertEquals("\"say \"\"hi\"\"\"", CsvExporter.field("say \"hi\""));
    }

    @Test
    @DisplayName("transactions export one value per column")
    void transactions() throws Exception {
        File f = dir.resolve("t.csv").toFile();
        CsvExporter.writeTransactions(f, List.of(new Transaction(LocalDateTime.of(2026, 11, 3, 9, 30), "BUY", "Apple",
            10, new BigDecimal("338.3200"), new BigDecimal("3383.20"))));
        List<String> lines = Files.readAllLines(f.toPath());
        assertEquals("Timestamp,Type,Stock,Quantity,Price,Total", lines.get(0));
        assertEquals("2026-11-03T09:30,BUY,Apple,10,338.3200,3383.20", lines.get(1));
    }

    @Test
    @DisplayName("portfolio export includes P&L columns")
    void portfolio() throws Exception {
        File f = dir.resolve("p.csv").toFile();
        CsvExporter.writePortfolio(f, List.of(new PortfolioEntry("Apple", 10, new BigDecimal("100.0000"), new BigDecimal("110.5000"))));
        List<String> lines = Files.readAllLines(f.toPath());
        assertEquals("Apple,10,100.0000,110.5000,1105.00,105.00,10.50", lines.get(1));
    }
}
