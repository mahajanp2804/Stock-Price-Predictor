package com.capstone.prediction;

import java.util.ArrayList;
import java.util.List;

/** The forecasters the app compares. */
public final class Forecasters {

    private Forecasters() {}

    /** Baseline: every future day equals the latest close (a "random walk"). Hard to beat for stock prices. */
    public static final Forecaster TODAYS_PRICE = new Forecaster() {
        @Override public String name() { return "Today's price"; }
        @Override public List<Double> forecast(List<Double> history, int horizon) {
            double last = history.get(history.size() - 1);
            List<Double> out = new ArrayList<>(horizon);
            for (int i = 0; i < horizon; i++) out.add(last);
            return out;
        }
    };

    /** Baseline: continue the average daily change of the last MODEL_WINDOW closes. */
    public static final Forecaster DRIFT = new Forecaster() {
        @Override public String name() { return "Average drift"; }
        @Override public List<Double> forecast(List<Double> history, int horizon) {
            int n = history.size();
            int start = Math.max(0, n - PredictionModule.MODEL_WINDOW);
            double last = history.get(n - 1);
            double perDay = n - 1 > start ? (last - history.get(start)) / (n - 1 - start) : 0;
            List<Double> out = new ArrayList<>(horizon);
            for (int i = 1; i <= horizon; i++) out.add(Math.max(0, last + perDay * i));
            return out;
        }
    };

    /** The app's original model: linear regression nudged by EMA and RSI (PredictionModule). */
    public static final Forecaster REGRESSION = new Forecaster() {
        @Override public String name() { return "Regression + EMA + RSI"; }
        @Override public List<Double> forecast(List<Double> history, int horizon) {
            return PredictionModule.predictFuturePrices(history, horizon);
        }
    };

    public static final List<Forecaster> ALL = List.of(TODAYS_PRICE, DRIFT, REGRESSION);
}
