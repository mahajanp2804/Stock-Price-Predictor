package com.capstone.ui;

import com.capstone.data.StockDataModule;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

/** Small Swing helpers shared by the tabs. */
final class Ui {

    static final Color GAIN = new Color(30, 130, 30);
    static final Color LOSS = new Color(180, 40, 40);

    private Ui() {}

    static void showError(Component parent, String msg) {
        JOptionPane.showMessageDialog(parent, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }

    static JComboBox<String> stockCombo(StockDataModule stocks) {
        return new JComboBox<>(stocks.getAllStockNames().toArray(new String[0]));
    }

    static DefaultTableModel readOnlyModel(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
    }

    static JTable table(DefaultTableModel model) {
        JTable table = new JTable(model);
        table.setRowHeight(24);
        table.getTableHeader().setReorderingAllowed(false);
        return table;
    }

    /** Right-aligned; red for values starting with "-", green for "+", normal text otherwise. */
    static TableCellRenderer pnlRenderer() {
        return new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int r, int c) {
                Component comp = super.getTableCellRendererComponent(t, v, sel, foc, r, c);
                String val = v == null ? "" : v.toString();
                comp.setForeground(val.startsWith("-") ? LOSS : val.startsWith("+") ? GAIN : t.getForeground());
                setHorizontalAlignment(RIGHT);
                return comp;
            }
        };
    }
}
