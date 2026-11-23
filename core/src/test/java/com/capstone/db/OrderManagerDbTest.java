package com.capstone.db;

import com.capstone.model.Order;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static com.capstone.model.Order.Side.*;
import static com.capstone.model.Order.Type.*;
import static org.junit.jupiter.api.Assertions.*;

class OrderManagerDbTest extends DbTestSupport {

    private final PortfolioManager pm = new PortfolioManager();
    private final OrderManager orders = new OrderManager(pm);
    private int user;

    @BeforeEach
    void createUser() throws Exception {
        assertEquals(AuthManager.RegisterResult.OK, new AuthManager().register("orderer", "password1"));
        user = Integer.parseInt(scalar("SELECT id FROM users WHERE username='orderer'"));
    }

    @Test
    @DisplayName("placed orders are listed open, with their trigger stored to 4 dp")
    void place() throws Exception {
        Order o = orders.place(user, "AAPL", BUY, LIMIT, 5, 95.5);
        assertEquals(Order.Status.OPEN, o.status());
        assertEquals(new BigDecimal("95.5000"), o.triggerPrice());
        assertEquals(List.of(o.id()), orders.list(user).stream().map(Order::id).toList());
        assertEquals(1, orders.countOpen(user, "AAPL"));
        assertEquals(0, orders.countOpen(user, "TSLA"));
    }

    @Test
    @DisplayName("invalid quantity or trigger is refused")
    void invalid() {
        assertThrows(IllegalArgumentException.class, () -> orders.place(user, "AAPL", BUY, LIMIT, 0, 95));
        assertThrows(IllegalArgumentException.class, () -> orders.place(user, "AAPL", BUY, LIMIT, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> orders.place(user, "AAPL", BUY, LIMIT, 1, Double.NaN));
    }

    @Test
    @DisplayName("users can cancel only their own open orders")
    void cancel() throws Exception {
        Order o = orders.place(user, "AAPL", BUY, LIMIT, 5, 95);
        new AuthManager().register("someone", "password1");
        int other = Integer.parseInt(scalar("SELECT id FROM users WHERE username='someone'"));
        assertFalse(orders.cancel(other, o.id()));
        assertTrue(orders.cancel(user, o.id()));
        assertFalse(orders.cancel(user, o.id()), "already cancelled");
        assertEquals(Order.Status.CANCELLED, orders.find(o.id()).status());
    }

    @Test
    @DisplayName("a limit buy waits until the price falls to it, then fills at the market price")
    void limitBuyFills() throws Exception {
        Order o = orders.place(user, "AAPL", BUY, LIMIT, 10, 95);
        assertTrue(orders.checkAndFill(Map.of("AAPL", 96.0)).isEmpty());
        assertEquals(Order.Status.OPEN, orders.find(o.id()).status());

        List<OrderManager.Outcome> out = orders.checkAndFill(Map.of("AAPL", 94.5));
        assertEquals(1, out.size());
        assertTrue(out.get(0).filled());
        Order filled = orders.find(o.id());
        assertEquals(Order.Status.FILLED, filled.status());
        assertEquals(new BigDecimal("94.5000"), filled.fillPrice());
        assertNotNull(filled.closedAt());
        assertEquals(new BigDecimal("9055.00"), pm.getUserBalance(user));
        assertEquals(10, pm.heldQuantity(user, "AAPL"));
        assertTrue(orders.checkAndFill(Map.of("AAPL", 90.0)).isEmpty(), "a filled order never fills again");
    }

    @Test
    @DisplayName("a stop-loss sells held shares when the price falls to it")
    void stopLoss() throws Exception {
        pm.buyStock(user, "TSLA", 4, 250);
        orders.place(user, "TSLA", SELL, STOP, 4, 230);
        assertTrue(orders.checkAndFill(Map.of("TSLA", 240.0)).isEmpty());
        assertTrue(orders.checkAndFill(Map.of("TSLA", 228.0)).get(0).filled());
        assertEquals(0, pm.heldQuantity(user, "TSLA"));
        assertEquals(new BigDecimal("9912.00"), pm.getUserBalance(user));   // 10000 - 1000 + 912
    }

    @Test
    @DisplayName("a triggered order that can't trade is rejected with the reason and changes nothing else")
    void rejected() throws Exception {
        Order big = orders.place(user, "MSFT", BUY, LIMIT, 100, 500);
        Order unheld = orders.place(user, "NVDA", SELL, STOP, 1, 200);
        List<OrderManager.Outcome> out = orders.checkAndFill(Map.of("MSFT", 490.0, "NVDA", 150.0));
        assertEquals(2, out.size());
        assertTrue(out.stream().noneMatch(OrderManager.Outcome::filled));
        Order r = orders.find(big.id());
        assertEquals(Order.Status.REJECTED, r.status());
        assertTrue(r.message().startsWith("Rejected: Insufficient balance"), r.message());
        assertNull(r.fillPrice());
        assertTrue(orders.find(unheld.id()).message().contains("don't hold any shares"));
        assertEquals(new BigDecimal("10000.00"), pm.getUserBalance(user));
        assertEquals("0", scalar("SELECT COUNT(*) FROM transactions"));
    }

    @Test
    @DisplayName("an order checked by many threads at once fills exactly once")
    void fillsOnce() throws Exception {
        Order o = orders.place(user, "AAPL", BUY, LIMIT, 1, 100);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<List<OrderManager.Outcome>>> futures = new ArrayList<>();
        for (int i = 0; i < 10; i++) futures.add(pool.submit(() -> { start.await(); return orders.checkAndFill(Map.of("AAPL", 99.0)); }));
        start.countDown();
        int fills = 0;
        for (Future<List<OrderManager.Outcome>> f : futures) fills += f.get(60, TimeUnit.SECONDS).size();
        pool.shutdown();
        assertEquals(1, fills);
        assertEquals("1", scalar("SELECT COUNT(*) FROM transactions"));
        assertEquals(Order.Status.FILLED, orders.find(o.id()).status());
    }

    @Test
    @DisplayName("at most MAX_OPEN_ORDERS open orders per user")
    void limit() throws Exception {
        for (int i = 0; i < OrderManager.MAX_OPEN_ORDERS; i++) orders.place(user, "AAPL", BUY, LIMIT, 1, 50 + i);
        assertThrows(IllegalArgumentException.class, () -> orders.place(user, "AAPL", BUY, LIMIT, 1, 10));
    }
}
