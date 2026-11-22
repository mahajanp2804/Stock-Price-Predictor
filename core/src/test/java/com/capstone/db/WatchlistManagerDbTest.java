package com.capstone.db;

import com.capstone.model.WatchItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class WatchlistManagerDbTest extends DbTestSupport {

    private final WatchlistManager watchlist = new WatchlistManager();
    private int user;

    @BeforeEach
    void createUser() throws Exception {
        assertEquals(AuthManager.RegisterResult.OK, new AuthManager().register("watcher", "password1"));
        user = Integer.parseInt(scalar("SELECT id FROM users WHERE username='watcher'"));
    }

    private List<String> symbols() throws Exception {
        return watchlist.list(user).stream().map(WatchItem::symbol).toList();
    }

    @Test
    @DisplayName("new users start with the five default stocks, sorted by symbol")
    void defaults() throws Exception {
        assertEquals(List.of("AAPL", "AMZN", "GOOGL", "MSFT", "TSLA"), symbols());
        assertEquals("Apple Inc", watchlist.list(user).get(0).companyName());
    }

    @Test
    @DisplayName("add, re-add and remove")
    void addRemove() throws Exception {
        assertEquals(WatchlistManager.AddResult.ADDED, watchlist.add(user, "NVDA", "NVIDIA Corporation"));
        assertEquals(WatchlistManager.AddResult.ALREADY_THERE, watchlist.add(user, "NVDA", "NVIDIA Corporation"));
        assertTrue(symbols().contains("NVDA"));
        watchlist.remove(user, "NVDA");
        watchlist.remove(user, "TSLA");
        assertEquals(List.of("AAPL", "AMZN", "GOOGL", "MSFT"), symbols());
    }

    @Test
    @DisplayName("watchlists are per user")
    void perUser() throws Exception {
        new AuthManager().register("other", "password1");
        int other = Integer.parseInt(scalar("SELECT id FROM users WHERE username='other'"));
        watchlist.add(user, "NVDA", "NVIDIA Corporation");
        assertFalse(watchlist.list(other).stream().anyMatch(w -> w.symbol().equals("NVDA")));
    }

    @Test
    @DisplayName("at most MAX_STOCKS per user, even when adds race")
    void limit() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<WatchlistManager.AddResult>> results = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            String symbol = "T" + i;
            results.add(pool.submit(() -> watchlist.add(user, symbol, "Test " + symbol)));
        }
        int added = 0, full = 0;
        for (Future<WatchlistManager.AddResult> f : results) {
            switch (f.get(60, TimeUnit.SECONDS)) {
                case ADDED -> added++;
                case FULL -> full++;
                default -> fail("unexpected duplicate");
            }
        }
        pool.shutdown();
        assertEquals(WatchlistManager.MAX_STOCKS - 5, added);
        assertEquals(30 - added, full);
        assertEquals(WatchlistManager.MAX_STOCKS, symbols().size());
    }

    @Test
    @DisplayName("held quantity is read per symbol")
    void heldQuantity() throws Exception {
        PortfolioManager pm = new PortfolioManager();
        assertEquals(0, pm.heldQuantity(user, "AAPL"));
        pm.buyStock(user, "AAPL", 3, 100);
        assertEquals(3, pm.heldQuantity(user, "AAPL"));
        assertEquals(0, pm.heldQuantity(user, "TSLA"));
    }
}
