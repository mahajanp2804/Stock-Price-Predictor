package com.capstone.web;

import com.capstone.data.SymbolSearch;
import com.capstone.db.PortfolioManager;
import com.capstone.db.WatchlistManager;
import com.capstone.model.PricePoint;
import com.capstone.model.Stock;
import com.capstone.model.WatchItem;
import com.capstone.prediction.BacktestResult;
import com.capstone.prediction.Backtester;
import com.capstone.prediction.Forecaster;
import com.capstone.prediction.Forecasters;
import com.capstone.prediction.PredictionModule;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/** Watchlist, symbol search, price history and backtested forecasts. */
@RestController
@RequestMapping("/api")
class StockController {

    record StockView(String symbol, String name, Double price, Double change30d, Instant liveAt, boolean available) {}
    record AddRequest(String symbol, String name) {}
    record ModelScore(String name, int samples, double averageError, double averageErrorPercent,
                      Double directionAccuracy, Double vsTodaysPrice) {}
    record ForecastDay(int day, double price, double low, double high) {}
    record ForecastView(String symbol, String name, int days, LocalDate lastDate, double lastClose,
                        LocalDate testedFrom, LocalDate testedTo, List<ModelScore> models,
                        String bestModel, String forecastModel, List<ForecastDay> forecast) {}

    private final StockService stocks;
    private final WatchlistManager watchlist = new WatchlistManager();
    private final PortfolioManager portfolio = new PortfolioManager();
    private final SymbolSearch search = new SymbolSearch();

    StockController(StockService stocks) { this.stocks = stocks; }

    @GetMapping("/watchlist")
    List<StockView> list(@RequestAttribute(AuthInterceptor.SESSION) SessionStore.Session s) throws SQLException {
        List<StockView> out = new ArrayList<>();
        for (WatchItem w : stocks.watchlist(s.userId())) out.add(view(w, stocks.ensureLoaded(w.symbol(), w.companyName())));
        return out;
    }

    @PostMapping("/watchlist")
    ResponseEntity<StockView> add(@RequestAttribute(AuthInterceptor.SESSION) SessionStore.Session s,
                                  @RequestBody AddRequest req) throws SQLException {
        String symbol = StockService.normalizeSymbol(req.symbol());
        String name = req.name() == null || req.name().isBlank() ? symbol : req.name().trim();
        if (name.length() > 200) name = name.substring(0, 200);
        switch (watchlist.add(s.userId(), symbol, name)) {
            case FULL -> throw new ApiException(HttpStatus.CONFLICT, "Your watchlist is full (" + WatchlistManager.MAX_STOCKS + " stocks). Remove one first.");
            case ALREADY_THERE -> throw new ApiException(HttpStatus.CONFLICT, symbol + " is already on your watchlist");
            case ADDED -> { }
        }
        Stock stock = stocks.ensureLoaded(symbol, name);
        if (stock == null) {
            watchlist.remove(s.userId(), symbol);
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "No price data is available for " + symbol + ", so it wasn't added");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(view(new WatchItem(symbol, name), stock));
    }

    @DeleteMapping("/watchlist/{symbol}")
    ResponseEntity<Void> remove(@RequestAttribute(AuthInterceptor.SESSION) SessionStore.Session s,
                                @PathVariable String symbol) throws SQLException {
        symbol = StockService.normalizeSymbol(symbol);
        stocks.requireWatched(s.userId(), symbol);
        int held = portfolio.heldQuantity(s.userId(), symbol);
        if (held > 0) throw new ApiException(HttpStatus.CONFLICT, String.format("You hold %d share(s) of %s. Sell them before removing it.", held, symbol));
        watchlist.remove(s.userId(), symbol);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/search")
    List<WatchItem> search(@RequestParam String q) throws IOException, InterruptedException {
        if (!search.isConfigured()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Search needs a Finnhub API key on the server");
        if (q.isBlank() || q.length() > 50) throw ApiException.badRequest("Search for 1–50 characters");
        return search.search(q);
    }

    @GetMapping("/stocks/{symbol}/history")
    List<PricePoint> history(@RequestAttribute(AuthInterceptor.SESSION) SessionStore.Session s,
                             @PathVariable String symbol, @RequestParam(defaultValue = "3M") String period) throws SQLException {
        Stock stock = stocks.requireStock(s.userId(), StockService.normalizeSymbol(symbol));
        int months = switch (period.toUpperCase()) {
            case "1M" -> 1; case "3M" -> 3; case "6M" -> 6; case "1Y" -> 12; case "ALL" -> 0;
            default -> throw ApiException.badRequest("period must be 1M, 3M, 6M, 1Y or ALL");
        };
        List<PricePoint> h = stock.getHistory();
        if (months == 0) return h;
        LocalDate from = h.get(h.size() - 1).date().minusMonths(months);
        return h.stream().filter(p -> !p.date().isBefore(from)).toList();
    }

    @GetMapping("/stocks/{symbol}/forecast")
    ForecastView forecast(@RequestAttribute(AuthInterceptor.SESSION) SessionStore.Session s,
                          @PathVariable String symbol, @RequestParam(defaultValue = "5") int days,
                          @RequestParam(required = false) String model) throws SQLException {
        if (days < 1 || days > 30) throw ApiException.badRequest("days must be between 1 and 30");
        Stock stock = stocks.requireStock(s.userId(), StockService.normalizeSymbol(symbol));
        List<PricePoint> history = stock.getHistory();
        List<Double> closes = stock.getPriceHistory();
        List<BacktestResult> results = Backtester.compareAll(closes, days);
        BacktestResult baseline = results.get(0);
        if (baseline.samples() == 0) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "Not enough history to test the models " + days
                + " day(s) ahead (need at least " + (PredictionModule.MODEL_WINDOW + days) + " days)");
        }
        List<ModelScore> scores = new ArrayList<>();
        for (BacktestResult r : results) {
            scores.add(new ModelScore(r.forecaster(), r.samples(), r.mae(), r.mape(),
                Double.isNaN(r.directionAccuracy()) ? null : r.directionAccuracy(),
                r == baseline ? null : r.skillVersus(baseline)));
        }
        BacktestResult best = results.stream().min(Comparator.comparingDouble(BacktestResult::mae)).orElseThrow();
        BacktestResult chosen = model == null || model.isBlank() ? best
            : results.stream().filter(r -> r.forecaster().equals(model)).findFirst()
                .orElseThrow(() -> ApiException.badRequest("Unknown model: " + model));
        Forecaster f = Forecasters.ALL.stream().filter(x -> x.name().equals(chosen.forecaster())).findFirst().orElseThrow();
        List<Double> points = f.forecast(closes, days);
        List<ForecastDay> forecast = new ArrayList<>();
        for (int d = 1; d <= days; d++) {
            double[] range = chosen.range(d, points.get(d - 1));
            forecast.add(new ForecastDay(d, points.get(d - 1), range[0], range[1]));
        }
        return new ForecastView(stock.getSymbol(), stock.getName(), days,
            history.get(history.size() - 1).date(), closes.get(closes.size() - 1),
            history.get(baseline.firstOrigin()).date(), history.get(baseline.lastOrigin()).date(),
            scores, best.forecaster(), chosen.forecaster(), forecast);
    }

    private static StockView view(WatchItem w, Stock stock) {
        if (stock == null) return new StockView(w.symbol(), w.companyName(), null, null, null, false);
        OptionalDouble change = stock.percentChangeSince(LocalDate.now().minusDays(30));
        return new StockView(w.symbol(), w.companyName(), stock.getLatestPrice(),
            change.isPresent() ? change.getAsDouble() : null, stock.getLiveUpdatedAt(), true);
    }
}
