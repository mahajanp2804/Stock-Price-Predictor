package com.capstone.prediction;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class BacktesterTest {

    /** A forecaster that always predicts the same price. */
    private static Forecaster constant(double price) {
        return new Forecaster() {
            @Override public String name() { return "Constant " + price; }
            @Override public List<Double> forecast(List<Double> history, int horizon) { return Collections.nCopies(horizon, price); }
        };
    }

    private static List<Double> line(int n, double start, double step) {
        List<Double> l = new ArrayList<>();
        for (int i = 0; i < n; i++) l.add(start + i * step);
        return l;
    }

    @Test
    @DisplayName("metrics match a hand-worked example")
    void handWorked() {
        // origins t=1 (last known 12, actual next 9) and t=2 (last known 9, actual next 11); forecast is always 10
        BacktestResult r = Backtester.run(List.of(10.0, 12.0, 9.0, 11.0), constant(10), 2, 1);
        assertEquals(2, r.samples());
        assertEquals(1.0, r.mae(), 1e-12);               // |9-10|, |11-10|
        assertEquals(1.0, r.rmse(), 1e-12);
        assertEquals((1.0 / 9 + 1.0 / 11) / 2 * 100, r.mape(), 1e-9);
        // t=1: predicted down (12→10), actual down (12→9) ✓; t=2: predicted up (9→10), actual up (9→11) ✓
        assertEquals(1.0, r.directionAccuracy(), 1e-12);
    }

    @Test
    @DisplayName("each forecast only sees closes up to its own day")
    void noLookAhead() {
        List<Double> closes = line(50, 100, 1);
        AtomicInteger checked = new AtomicInteger();
        Forecaster spy = new Forecaster() {
            @Override public String name() { return "spy"; }
            @Override public List<Double> forecast(List<Double> history, int horizon) {
                // the last close it sees must be followed by `horizon` more closes in the full list
                assertTrue(history.size() + horizon <= closes.size());
                assertEquals(closes.subList(0, history.size()), history);
                checked.incrementAndGet();
                return Collections.nCopies(horizon, history.get(history.size() - 1));
            }
        };
        BacktestResult r = Backtester.run(closes, spy, 30, 5);
        assertEquals(50 - 30 - 5 + 1, r.samples());
        assertEquals(r.samples(), checked.get());
        assertEquals(29, r.firstOrigin());
        assertEquals(44, r.lastOrigin());
    }

    @Test
    @DisplayName("on a straight line, drift is exact and today's price is off by slope × horizon")
    void straightLine() {
        List<Double> closes = line(120, 100, 0.5);
        BacktestResult naive = Backtester.run(closes, Forecasters.TODAYS_PRICE, 30, 5);
        BacktestResult drift = Backtester.run(closes, Forecasters.DRIFT, 30, 5);
        BacktestResult regression = Backtester.run(closes, Forecasters.REGRESSION, 30, 5);
        assertEquals(2.5, naive.mae(), 1e-9);
        assertEquals(0.0, drift.mae(), 1e-9);
        // Known weakness of the original model: on a steady uptrend the lagging EMA and the
        // RSI "overbought" damping (RSI = 100 here) drag its forecast below the line,
        // so it does worse than simply using today's price
        assertTrue(regression.mae() > naive.mae(), "regression " + regression.mae());
        assertEquals(1.0, drift.directionAccuracy(), 1e-12);
        assertEquals(1.0, drift.skillVersus(naive), 1e-9);
    }

    @Test
    @DisplayName("today's-price baseline never predicts a move, so direction accuracy is n/a")
    void naiveDirectionUndefined() {
        BacktestResult r = Backtester.run(List.of(10.0, 11.0, 9.0, 12.0, 8.0), Forecasters.TODAYS_PRICE, 1, 1);
        assertTrue(Double.isNaN(r.directionAccuracy()));
        assertEquals(4, r.samples());
    }

    @Test
    @DisplayName("80% range comes from the 10th and 90th percentile of past errors")
    void range() {
        // residuals for horizon 1 with a constant-100 forecast are 1..11 (actual − 100)
        List<Double> closes = new ArrayList<>(List.of(100.0));
        for (int i = 1; i <= 11; i++) closes.add(100.0 + i);
        BacktestResult r = Backtester.run(closes, constant(100), 1, 1);
        double[] range = r.range(1, 200);
        assertEquals(202.0, range[0], 1e-9);   // 10th percentile of 1..11 = 2
        assertEquals(210.0, range[1], 1e-9);   // 90th percentile = 10
    }

    @Test
    @DisplayName("too little history gives zero samples and NaN metrics, not an error")
    void tooShort() {
        BacktestResult r = Backtester.run(line(10, 100, 1), Forecasters.DRIFT, 30, 5);
        assertEquals(0, r.samples());
        assertTrue(Double.isNaN(r.mae()));
        assertTrue(Double.isNaN(r.range(1, 100)[0]));
    }

    @Test
    @DisplayName("compareAll scores every model over the same days")
    void compareAll() {
        List<BacktestResult> all = Backtester.compareAll(line(100, 50, 0.2), 3);
        assertEquals(Forecasters.ALL.size(), all.size());
        assertTrue(all.stream().allMatch(r -> r.samples() == all.get(0).samples() && r.samples() > 0));
    }

    @Test
    @DisplayName("quantile interpolates between sorted values")
    void quantile() {
        assertEquals(2.5, Backtester.quantile(new double[]{1, 2, 3, 4}, 0.5), 1e-12);
        assertEquals(7.0, Backtester.quantile(new double[]{7}, 0.9), 1e-12);
    }
}
