package com.capstone.prediction;

/**
 * How one forecaster did when replayed over past prices.
 *
 * Errors are measured on the final day of the horizon (e.g. the price 5 trading
 * days after each forecast). Metrics are NaN when there were no forecasts to score.
 *
 * @param samples             number of past forecasts scored
 * @param firstOrigin         index in the price list of the last close known to the first forecast
 * @param lastOrigin          the same for the last forecast
 * @param mae                 mean absolute error, in dollars
 * @param rmse                root mean squared error, in dollars (punishes big misses)
 * @param mape                mean absolute error as a percent of the actual price
 * @param directionAccuracy   share (0–1) of forecasts that called up vs down correctly; NaN if the model never predicts a move
 * @param lowResidual         per day ahead (index 0 = day +1): 10th percentile of actual − forecast
 * @param highResidual        per day ahead: 90th percentile of actual − forecast
 */
public record BacktestResult(String forecaster, int horizon, int samples, int firstOrigin, int lastOrigin,
                             double mae, double rmse, double mape, double directionAccuracy,
                             double[] lowResidual, double[] highResidual) {

    /** 80% range for a forecast of {@code point} for {@code day} days ahead (1-based), from past errors. */
    public double[] range(int day, double point) {
        if (samples == 0) return new double[]{Double.NaN, Double.NaN};
        return new double[]{Math.max(0, point + lowResidual[day - 1]), point + highResidual[day - 1]};
    }

    /** How much smaller this result's MAE is than {@code baseline}'s: +0.10 = 10% better, −0.10 = 10% worse. */
    public double skillVersus(BacktestResult baseline) {
        return 1 - mae / baseline.mae;
    }
}
