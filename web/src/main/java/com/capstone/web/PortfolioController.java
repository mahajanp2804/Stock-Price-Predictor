package com.capstone.web;

import com.capstone.db.PortfolioManager;
import com.capstone.model.PortfolioEntry;
import com.capstone.model.Stock;
import com.capstone.model.TradeResult;
import com.capstone.model.Transaction;
import com.capstone.portfolio.PortfolioAnalytics;
import com.capstone.portfolio.PortfolioLedger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Balance, holdings, trades and transaction history for the logged-in user. */
@RestController
@RequestMapping("/api")
class PortfolioController {

    record Me(String username, BigDecimal balance) {}
    record TradeRequest(String symbol, String side, Integer quantity) {}
    record TradeResponse(boolean success, String message, BigDecimal balance, double price, boolean livePrice) {}
    record TransactionView(LocalDateTime timestamp, String type, String stockName, int quantity,
                           BigDecimal price, BigDecimal total, BigDecimal realizedPnL) {}
    record ValueView(LocalDate date, BigDecimal value) {}
    record ReportView(PortfolioAnalytics.Summary summary, List<ValueView> history) {}

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

    /** Cash, holdings, total return, realised/unrealised P&L, and daily total value since the first trade. */
    @GetMapping("/portfolio/report")
    ReportView report(@RequestAttribute(AuthInterceptor.SESSION) SessionStore.Session s) throws SQLException {
        PortfolioAnalytics.Report r = analytics().report(s.userId());
        return new ReportView(r.summary(), r.history().stream().map(v -> new ValueView(v.date(), v.total())).toList());
    }

    /** Newest first; sells carry their realised P&L. */
    @GetMapping("/transactions")
    List<TransactionView> transactions(@RequestAttribute(AuthInterceptor.SESSION) SessionStore.Session s) throws SQLException {
        return analytics().report(s.userId()).transactions().stream().map(PortfolioController::view).toList();
    }

    private PortfolioAnalytics analytics() { return new PortfolioAnalytics(portfolio, stocks.data); }

    private static TransactionView view(PortfolioLedger.Entry e) {
        Transaction t = e.transaction();
        return new TransactionView(t.timestamp(), t.type(), t.stockName(), t.quantity(), t.price(), t.total(), e.realizedPnL());
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
