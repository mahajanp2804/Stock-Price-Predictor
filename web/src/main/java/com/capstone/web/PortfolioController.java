package com.capstone.web;

import com.capstone.db.PortfolioManager;
import com.capstone.model.PortfolioEntry;
import com.capstone.model.Stock;
import com.capstone.model.TradeResult;
import com.capstone.model.Transaction;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/** Balance, holdings, trades and transaction history for the logged-in user. */
@RestController
@RequestMapping("/api")
class PortfolioController {

    record Me(String username, BigDecimal balance) {}
    record TradeRequest(String symbol, String side, Integer quantity) {}
    record TradeResponse(boolean success, String message, BigDecimal balance, double price, boolean livePrice) {}

    static final int MAX_QUANTITY = 1_000_000;

    private final StockService stocks;
    private final PortfolioManager portfolio = new PortfolioManager();

    PortfolioController(StockService stocks) { this.stocks = stocks; }

    @GetMapping("/me")
    Me me(@RequestAttribute(AuthInterceptor.SESSION) SessionStore.Session s) throws SQLException {
        return new Me(s.username(), portfolio.getUserBalance(s.userId()));
    }

    @GetMapping("/portfolio")
    List<PortfolioEntry> holdings(@RequestAttribute(AuthInterceptor.SESSION) SessionStore.Session s) throws SQLException {
        Map<String, Double> live = stocks.data.latestPrices();
        return portfolio.viewPortfolioWithPnL(s.userId(), live);
    }

    @GetMapping("/transactions")
    List<Transaction> transactions(@RequestAttribute(AuthInterceptor.SESSION) SessionStore.Session s) throws SQLException {
        return portfolio.viewTransactions(s.userId());
    }

    /** Fetches a fresh quote, then buys or sells at it. 422 if the trade is refused (e.g. not enough cash). */
    @PostMapping("/trades")
    ResponseEntity<TradeResponse> trade(@RequestAttribute(AuthInterceptor.SESSION) SessionStore.Session s,
                                        @RequestBody TradeRequest req) throws SQLException {
        String symbol = StockService.normalizeSymbol(req.symbol());
        String side = req.side() == null ? "" : req.side().trim().toUpperCase();
        if (!side.equals("BUY") && !side.equals("SELL")) throw ApiException.badRequest("side must be BUY or SELL");
        if (req.quantity() == null || req.quantity() < 1 || req.quantity() > MAX_QUANTITY) {
            throw ApiException.badRequest("quantity must be between 1 and " + MAX_QUANTITY);
        }
        Stock stock = stocks.requireStock(s.userId(), symbol);
        boolean live = stocks.data.refreshQuote(symbol);
        double price = stock.getLatestPrice();
        TradeResult result = side.equals("BUY")
            ? portfolio.buyStock(s.userId(), symbol, req.quantity(), price)
            : portfolio.sellStock(s.userId(), symbol, req.quantity(), price);
        TradeResponse body = new TradeResponse(result.success(), result.message(), result.newBalance(), price, live);
        return ResponseEntity.status(result.success() ? HttpStatus.OK : HttpStatus.UNPROCESSABLE_CONTENT).body(body);
    }
}
