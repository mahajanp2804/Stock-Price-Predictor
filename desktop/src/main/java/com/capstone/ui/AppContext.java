package com.capstone.ui;

import com.capstone.data.StockDataModule;
import com.capstone.db.PortfolioManager;
import com.capstone.db.WatchlistManager;

import javax.swing.JFrame;

/**
 * What every tab needs: the data sources, the logged-in user, the main window
 * (for dialogs), the shared stock drop-downs, and a callback to run after a trade
 * changes the balance or prices.
 */
record AppContext(StockDataModule stocks, PortfolioManager portfolio, WatchlistManager watchlist, int userId,
                  JFrame frame, StockCombos combos, Runnable afterTrade) {}
