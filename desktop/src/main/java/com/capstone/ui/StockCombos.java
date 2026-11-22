package com.capstone.ui;

import com.capstone.data.StockDataModule;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/** Creates the stock drop-downs used by the tabs and keeps them in step with the loaded stocks. UI thread only. */
final class StockCombos {

    private final StockDataModule stocks;
    private final List<JComboBox<String>> combos = new ArrayList<>();

    StockCombos(StockDataModule stocks) { this.stocks = stocks; }

    JComboBox<String> create() {
        JComboBox<String> combo = new JComboBox<>();
        combos.add(combo);
        fill(combo);
        return combo;
    }

    /** Re-reads the loaded stocks into every drop-down, keeping each one's selection if it still exists. */
    void refreshAll() {
        combos.forEach(this::fill);
    }

    private void fill(JComboBox<String> combo) {
        Object selected = combo.getSelectedItem();
        combo.removeAllItems();
        for (String symbol : stocks.getSymbols()) combo.addItem(symbol);
        if (selected != null) combo.setSelectedItem(selected);
    }
}
