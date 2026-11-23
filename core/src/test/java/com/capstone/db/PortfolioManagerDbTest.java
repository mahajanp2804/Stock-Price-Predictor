package com.capstone.db;

import com.capstone.model.PortfolioEntry;
import com.capstone.model.TradeResult;
import com.capstone.model.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PortfolioManagerDbTest extends DbTestSupport {

    private final PortfolioManager pm = new PortfolioManager();
    private int user;

    @BeforeEach
    void createUser() throws Exception {
        assertEquals(AuthManager.RegisterResult.OK, new AuthManager().register("trader", "password1"));
        user = Integer.parseInt(scalar("SELECT id FROM users WHERE username='trader'"));
    }

    private BigDecimal balance() throws Exception { return pm.getUserBalance(user); }

    private static BigDecimal bd(String s) { return new BigDecimal(s); }

    @Test
    @DisplayName("new users start with exactly $10,000.00")
    void startingBalance() throws Exception {
        assertEquals(bd("10000.00"), balance());
    }

    @Test
    @DisplayName("buy deducts the exact total and records the position")
    void buy() throws Exception {
        TradeResult r = pm.buyStock(user, "AAPL", 10, 338.32);
        assertTrue(r.success());
        assertTrue(r.message().startsWith("Bought 10 of AAPL"), r.message());
        assertEquals(bd("6616.80"), r.newBalance());
        assertEquals(bd("6616.80"), balance());
        List<PortfolioEntry> holdings = pm.viewPortfolioWithPnL(user, Map.of());
        assertEquals(1, holdings.size());
        assertEquals(10, holdings.get(0).quantity);
        assertEquals(bd("338.3200"), holdings.get(0).avgPrice);
    }

    @Test
    @DisplayName("prices that drift in double arithmetic are charged exactly")
    void noFloatingPointDrift() throws Exception {
        pm.buyStock(user, "AAPL", 3, 0.1 + 0.2); // 0.30000000000000004 as a double
        assertEquals(bd("9999.10"), balance());
    }

    @Test
    @DisplayName("buying more updates the weighted average price")
    void averagePrice() throws Exception {
        pm.buyStock(user, "AAPL", 10, 100);
        pm.buyStock(user, "AAPL", 20, 130);
        assertEquals("120.0000", scalar("SELECT avg_price FROM portfolio"));
        assertEquals("30", scalar("SELECT quantity FROM portfolio"));
    }

    @Test
    @DisplayName("a buy larger than the balance is rejected and changes nothing")
    void insufficientBalance() throws Exception {
        TradeResult r = pm.buyStock(user, "TSLA", 1000, 400);
        assertFalse(r.success());
        assertNull(r.newBalance());
        assertTrue(r.message().startsWith("Insufficient balance"), r.message());
        assertEquals(bd("10000.00"), balance());
        assertEquals("0", scalar("SELECT COUNT(*) FROM transactions"));
        assertEquals("0", scalar("SELECT COUNT(*) FROM portfolio"));
    }

    @Test
    @DisplayName("zero or negative quantities are rejected")
    void invalidQuantity() throws Exception {
        assertEquals("Quantity must be greater than zero.", pm.buyStock(user, "AAPL", 0, 100).message());
        assertEquals("Quantity must be greater than zero.", pm.sellStock(user, "AAPL", -1, 100).message());
        assertEquals(bd("10000.00"), balance());
    }

    @Test
    @DisplayName("selling more than held, or a stock never held, is rejected")
    void oversell() throws Exception {
        pm.buyStock(user, "AAPL", 5, 100);
        assertEquals("You only hold 5 share(s) of AAPL.", pm.sellStock(user, "AAPL", 6, 100).message());
        assertEquals("You don't hold any shares of TSLA.", pm.sellStock(user, "TSLA", 1, 100).message());
        assertEquals(bd("9500.00"), balance());
    }

    @Test
    @DisplayName("partial sell keeps the position; selling the rest removes it")
    void sell() throws Exception {
        pm.buyStock(user, "AAPL", 10, 100);
        pm.sellStock(user, "AAPL", 4, 110.005);   // 440.02 after rounding
        assertEquals(bd("9440.02"), balance());
        assertEquals("6", scalar("SELECT quantity FROM portfolio"));
        assertEquals("100.0000", scalar("SELECT avg_price FROM portfolio"), "selling doesn't change cost basis");
        pm.sellStock(user, "AAPL", 6, 120);
        assertEquals(bd("10160.02"), balance());
        assertTrue(pm.viewPortfolioWithPnL(user, Map.of()).isEmpty());
    }

    @Test
    @DisplayName("transactions are logged newest first")
    void transactions() throws Exception {
        pm.buyStock(user, "AAPL", 2, 100);
        pm.buyStock(user, "TSLA", 1, 200);
        pm.sellStock(user, "AAPL", 1, 105);
        List<Transaction> txns = pm.viewTransactions(user);
        assertEquals(3, txns.size());
        assertEquals("SELL", txns.get(0).type());
        assertEquals(bd("105.00"), txns.get(0).total());
        assertEquals("AAPL", txns.get(2).stockName());
    }

    @Test
    @DisplayName("portfolio is valued at live prices, falling back to the cached DB price")
    void valuation() throws Exception {
        pm.buyStock(user, "AAPL", 10, 100);
        pm.buyStock(user, "TSLA", 2, 200);
        exec("UPDATE stocks SET current_price = 190 WHERE stock_name = 'TSLA'");
        List<PortfolioEntry> holdings = pm.viewPortfolioWithPnL(user, Map.of("AAPL", 110.0));
        PortfolioEntry apple = holdings.get(0), tesla = holdings.get(1);
        assertEquals(bd("100.00"), apple.unrealizedPnL);
        assertEquals(bd("-20.00"), tesla.unrealizedPnL);
    }

    @Test
    @DisplayName("replaying the transaction log reproduces the stored positions and balance")
    void ledgerMatchesStoredPortfolio() throws Exception {
        pm.buyStock(user, "AAPL", 7, 101.37);
        pm.buyStock(user, "AAPL", 5, 99.991);
        pm.buyStock(user, "MSFT", 3, 412.5);
        pm.sellStock(user, "AAPL", 4, 105.2);
        pm.buyStock(user, "AAPL", 2, 97.33);
        pm.sellStock(user, "MSFT", 3, 400);

        List<Transaction> oldestFirst = new ArrayList<>(pm.viewTransactions(user));
        java.util.Collections.reverse(oldestFirst);
        com.capstone.portfolio.PortfolioLedger.Result r = com.capstone.portfolio.PortfolioLedger.replay(oldestFirst);

        assertEquals(scalar("SELECT avg_price FROM portfolio"), r.positions().get("AAPL").avgCost().toPlainString());
        assertEquals(scalar("SELECT quantity FROM portfolio"), String.valueOf(r.positions().get("AAPL").quantity()));
        assertFalse(r.positions().containsKey("MSFT"));
        assertEquals(balance(), bd("10000.00").add(r.netCashFlow()));
    }

    @Test
    @DisplayName("concurrent buys can't overspend: 20 × $1,000 against $10,000 → exactly 10 succeed")
    void concurrentBuys() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                if (pm.buyStock(user, "MSFT", 1, 1000).success()) succeeded.incrementAndGet();
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) f.get(60, TimeUnit.SECONDS);
        pool.shutdown();

        assertEquals(10, succeeded.get());
        assertEquals(bd("0.00"), balance());
        assertEquals("10", scalar("SELECT quantity FROM portfolio"));
        assertEquals("10", scalar("SELECT COUNT(*) FROM transactions"));
    }
}
