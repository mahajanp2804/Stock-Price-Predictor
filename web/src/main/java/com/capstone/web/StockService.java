package com.capstone.web;

import com.capstone.data.StockDataModule;
import com.capstone.db.WatchlistManager;
import com.capstone.model.Stock;
import com.capstone.model.WatchItem;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.List;

/**
 * One StockDataModule shared by every user. Stocks are loaded the first time any
 * user needs them (from the DB cache when fresh) and their live quotes refresh
 * every minute.
 */
@Service
class StockService {

    final StockDataModule data = new StockDataModule();
    private final WatchlistManager watchlist = new WatchlistManager();

    /** The loaded stock, loading it first if needed; null if no prices exist for it. */
    Stock ensureLoaded(String symbol, String companyName) {
        Stock stock = data.getStock(symbol);
        if (stock != null) return stock;
        synchronized (this) {
            stock = data.getStock(symbol);
            if (stock == null && data.load(symbol, companyName)) {
                data.refreshQuote(symbol);
                stock = data.getStock(symbol);
            }
            return stock;
        }
    }

    /** The user's watchlist entry for symbol; 404 if it isn't on their watchlist. */
    WatchItem requireWatched(int userId, String symbol) throws SQLException {
        return watchlist.list(userId).stream().filter(w -> w.symbol().equals(symbol)).findFirst()
            .orElseThrow(() -> ApiException.notFound(symbol + " is not on your watchlist"));
    }

    /** A watched, loaded stock; 404 if not watched, 503 if no prices are available. */
    Stock requireStock(int userId, String symbol) throws SQLException {
        WatchItem item = requireWatched(userId, symbol);
        Stock stock = ensureLoaded(item.symbol(), item.companyName());
        if (stock == null) throw new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "No price data available for " + symbol + " right now");
        return stock;
    }

    List<WatchItem> watchlist(int userId) throws SQLException { return watchlist.list(userId); }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    void refreshQuotes() {
        data.refreshQuotes();
    }

    /** Tickers are 1–10 upper-case letters, digits, dots or dashes. */
    static String normalizeSymbol(String symbol) {
        String s = symbol == null ? "" : symbol.trim().toUpperCase();
        if (!s.matches("[A-Z0-9.\\-]{1,10}")) throw ApiException.badRequest("Invalid ticker symbol");
        return s;
    }
}
