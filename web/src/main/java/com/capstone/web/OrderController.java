package com.capstone.web;

import com.capstone.db.OrderManager;
import com.capstone.db.PortfolioManager;
import com.capstone.model.Order;
import com.capstone.model.Stock;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/** Limit and stop orders for the logged-in user. Open orders are filled by StockService's quote refresh. */
@RestController
@RequestMapping("/api/orders")
class OrderController {

    record PlaceRequest(String symbol, String side, String type, Integer quantity, Double triggerPrice) {}

    private final StockService stocks;
    private final OrderManager orders;

    OrderController(StockService stocks) {
        this.stocks = stocks;
        this.orders = stocks.orders;
    }

    /** Open orders first, then the 50 most recently closed. */
    @GetMapping
    List<Order> list(@RequestAttribute(AuthInterceptor.SESSION) SessionStore.Session s) throws SQLException {
        return orders.list(s.userId());
    }

    /** Places an order and checks it against the current price straight away (it may come back FILLED). */
    @PostMapping
    ResponseEntity<Order> place(@RequestAttribute(AuthInterceptor.SESSION) SessionStore.Session s,
                                @RequestBody PlaceRequest req) throws SQLException {
        String symbol = StockService.normalizeSymbol(req.symbol());
        Order.Side side = parse(Order.Side.class, req.side(), "side must be BUY or SELL");
        Order.Type type = parse(Order.Type.class, req.type(), "type must be LIMIT or STOP");
        if (req.quantity() == null || req.triggerPrice() == null) throw ApiException.badRequest("quantity and triggerPrice are required");
        Stock stock = stocks.requireStock(s.userId(), symbol);
        Order order;
        try {
            order = orders.place(s.userId(), symbol, side, type, req.quantity(), req.triggerPrice());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest(e.getMessage());
        }
        orders.checkAndFill(Map.of(symbol, stock.getLatestPrice()));
        return ResponseEntity.status(HttpStatus.CREATED).body(orders.find(order.id()));
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> cancel(@RequestAttribute(AuthInterceptor.SESSION) SessionStore.Session s,
                                @PathVariable int id) throws SQLException {
        if (!orders.cancel(s.userId(), id)) throw ApiException.notFound("No open order #" + id);
        return ResponseEntity.noContent().build();
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value, String error) {
        try {
            return Enum.valueOf(type, value == null ? "" : value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest(error);
        }
    }
}
