package com.capstone.prediction;

import java.util.List;

/** A way of forecasting future daily closes from past ones. */
public interface Forecaster {

    /** Short name shown in the UI. */
    String name();

    /**
     * Forecasts the next {@code horizon} closes.
     *
     * @param history daily closes up to and including the latest day, oldest first
     * @return exactly {@code horizon} prices, day +1 first
     */
    List<Double> forecast(List<Double> history, int horizon);
}
