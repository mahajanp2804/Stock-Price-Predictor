package com.capstone.prediction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Walk-forward backtest: for every past day t, forecast using only closes up to t,
 * then compare against the closes that actually followed. No future data leaks
 * into any forecast.
 */
public final class Backtester {

    /** 10th / 90th percentiles of past errors give an 80% range. */
    static final double LOW_Q = 0.10, HIGH_Q = 0.90;

    private Backtester() {}

    /**
     * @param closes     daily closes, oldest first
     * @param minHistory closes every forecast gets at least (the first forecast is made after this many days)
     * @param horizon    trading days ahead to forecast and score
     */
    public static BacktestResult run(List<Double> closes, Forecaster forecaster, int minHistory, int horizon) {
        if (horizon < 1) throw new IllegalArgumentException("horizon must be at least 1");
        int firstOrigin = Math.max(1, minHistory) - 1;
        int lastOrigin  = closes.size() - 1 - horizon;

        List<double[]> residualsByDay = new ArrayList<>();
        for (int d = 0; d < horizon; d++) residualsByDay.add(new double[Math.max(0, lastOrigin - firstOrigin + 1)]);

        double absSum = 0, sqSum = 0, pctSum = 0;
        int samples = 0, directionCalls = 0, directionHits = 0;
        for (int t = firstOrigin; t <= lastOrigin; t++) {
            List<Double> known = closes.subList(0, t + 1);
            List<Double> forecast = forecaster.forecast(known, horizon);
            double last = closes.get(t);
            for (int d = 0; d < horizon; d++) {
                residualsByDay.get(d)[samples] = closes.get(t + 1 + d) - forecast.get(d);
            }
            double predicted = forecast.get(horizon - 1), actual = closes.get(t + horizon);
            double err = actual - predicted;
            absSum += Math.abs(err);
            sqSum  += err * err;
            pctSum += Math.abs(err) / actual * 100;
            double predictedMove = Math.signum(predicted - last), actualMove = Math.signum(actual - last);
            if (predictedMove != 0 && actualMove != 0) {
                directionCalls++;
                if (predictedMove == actualMove) directionHits++;
            }
            samples++;
        }

        if (samples == 0) {
            return new BacktestResult(forecaster.name(), horizon, 0, firstOrigin, lastOrigin,
                Double.NaN, Double.NaN, Double.NaN, Double.NaN, new double[horizon], new double[horizon]);
        }
        double[] low = new double[horizon], high = new double[horizon];
        for (int d = 0; d < horizon; d++) {
            double[] r = residualsByDay.get(d);
            Arrays.sort(r);
            low[d]  = quantile(r, LOW_Q);
            high[d] = quantile(r, HIGH_Q);
        }
        return new BacktestResult(forecaster.name(), horizon, samples, firstOrigin, lastOrigin,
            absSum / samples, Math.sqrt(sqSum / samples), pctSum / samples,
            directionCalls == 0 ? Double.NaN : (double) directionHits / directionCalls, low, high);
    }

    /** Backtests every forecaster in Forecasters.ALL over the same days. */
    public static List<BacktestResult> compareAll(List<Double> closes, int horizon) {
        List<BacktestResult> results = new ArrayList<>();
        for (Forecaster f : Forecasters.ALL) results.add(run(closes, f, PredictionModule.MODEL_WINDOW, horizon));
        return results;
    }

    /** Linear-interpolated quantile of sorted values. */
    static double quantile(double[] sorted, double q) {
        if (sorted.length == 1) return sorted[0];
        double pos = q * (sorted.length - 1);
        int lo = (int) Math.floor(pos);
        int hi = Math.min(lo + 1, sorted.length - 1);
        return sorted[lo] + (sorted[hi] - sorted[lo]) * (pos - lo);
    }
}
