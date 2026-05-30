package com.capstone.prediction;

import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 4: Unit tests for PredictionModule.
 * Run with: mvn test
 */
class PredictionModuleTest {

    // ----------------------------------------------------------------
    // Test data helpers
    // ----------------------------------------------------------------

    /** Flat price list — no trend. */
    private List<Double> flatPrices(int n, double value) {
        List<Double> list = new ArrayList<>();
        for (int i = 0; i < n; i++) list.add(value);
        return list;
    }

    /** Linearly rising prices: start, start+step, start+2*step … */
    private List<Double> risingPrices(int n, double start, double step) {
        List<Double> list = new ArrayList<>();
        for (int i = 0; i < n; i++) list.add(start + i * step);
        return list;
    }

    /** Linearly falling prices. */
    private List<Double> fallingPrices(int n, double start, double step) {
        List<Double> list = new ArrayList<>();
        for (int i = 0; i < n; i++) list.add(start - i * step);
        return list;
    }

    // ----------------------------------------------------------------
    // predictFuturePrices()
    // ----------------------------------------------------------------

    @Test
    @DisplayName("predictFuturePrices: returns correct number of results")
    void futurePrices_returnsCorrectCount() {
        List<Double> prices = risingPrices(10, 100, 1);
        List<Double> result = PredictionModule.predictFuturePrices(prices, 5);
        assertEquals(5, result.size(), "Should return exactly 5 predicted prices");
    }

    @Test
    @DisplayName("predictFuturePrices: all predicted prices are positive")
    void futurePrices_allPositive() {
        List<Double> prices = risingPrices(10, 100, 2);
        PredictionModule.predictFuturePrices(prices, 7)
            .forEach(p -> assertTrue(p > 0, "Predicted price must be positive, got: " + p));
    }

    @Test
    @DisplayName("predictFuturePrices: rising trend produces increasing predictions")
    void futurePrices_risingTrendIncreasesOverTime() {
        List<Double> prices = risingPrices(20, 100, 5);
        List<Double> result = PredictionModule.predictFuturePrices(prices, 5);
        for (int i = 1; i < result.size(); i++) {
            assertTrue(result.get(i) >= result.get(i - 1),
                "Rising trend should produce non-decreasing predictions");
        }
    }

    @Test
    @DisplayName("predictFuturePrices: empty input returns empty list")
    void futurePrices_emptyInput_returnsEmpty() {
        assertTrue(PredictionModule.predictFuturePrices(Collections.emptyList(), 5).isEmpty());
    }

    @Test
    @DisplayName("predictFuturePrices: zero daysAhead returns empty list")
    void futurePrices_zeroDays_returnsEmpty() {
        assertTrue(PredictionModule.predictFuturePrices(risingPrices(10, 100, 1), 0).isEmpty());
    }

    @Test
    @DisplayName("predictFuturePrices: null input returns empty list")
    void futurePrices_nullInput_returnsEmpty() {
        assertTrue(PredictionModule.predictFuturePrices(null, 5).isEmpty());
    }

    // ----------------------------------------------------------------
    // predictTrend()
    // ----------------------------------------------------------------

    @Test
    @DisplayName("predictTrend: strongly rising 20-point series → Strong Uptrend")
    void trend_strongRising_strongUptrend() {
        List<Double> prices = risingPrices(25, 100, 5);
        String trend = PredictionModule.predictTrend(prices);
        assertTrue(trend.contains("Uptrend"), "Expected uptrend, got: " + trend);
    }

    @Test
    @DisplayName("predictTrend: strongly falling 20-point series → Strong Downtrend")
    void trend_strongFalling_strongDowntrend() {
        List<Double> prices = fallingPrices(25, 200, 5);
        String trend = PredictionModule.predictTrend(prices);
        assertTrue(trend.contains("Downtrend"), "Expected downtrend, got: " + trend);
    }

    @Test
    @DisplayName("predictTrend: flat 20-point series → Sideways")
    void trend_flat_sideways() {
        List<Double> prices = flatPrices(20, 150.0);
        String trend = PredictionModule.predictTrend(prices);
        assertEquals("Sideways", trend);
    }

    @Test
    @DisplayName("predictTrend: null input → Insufficient data")
    void trend_nullInput_insufficientData() {
        assertEquals("Insufficient data", PredictionModule.predictTrend(null));
    }

    @Test
    @DisplayName("predictTrend: single price → Insufficient data")
    void trend_singlePrice_insufficientData() {
        assertEquals("Insufficient data", PredictionModule.predictTrend(List.of(100.0)));
    }

    @Test
    @DisplayName("predictTrend: short series falls back to % change logic")
    void trend_shortSeries_fallbackLogic() {
        // 5 prices rising strongly — should use % fallback and return Uptrend
        List<Double> prices = risingPrices(5, 100, 10);
        String trend = PredictionModule.predictTrend(prices);
        assertTrue(trend.contains("Uptrend"), "Short rising series should be Uptrend, got: " + trend);
    }

    // ----------------------------------------------------------------
    // sma()
    // ----------------------------------------------------------------

    @Test
    @DisplayName("sma: correct average over full window")
    void sma_correctAverage() {
        List<Double> prices = List.of(10.0, 20.0, 30.0, 40.0, 50.0);
        assertEquals(30.0, PredictionModule.sma(prices, 5), 0.001);
    }

    @Test
    @DisplayName("sma: window larger than list uses full list")
    void sma_windowLargerThanList() {
        List<Double> prices = List.of(10.0, 20.0, 30.0);
        assertEquals(20.0, PredictionModule.sma(prices, 10), 0.001);
    }

    @Test
    @DisplayName("sma: window of 1 returns last price")
    void sma_windowOfOne() {
        List<Double> prices = List.of(10.0, 20.0, 30.0);
        assertEquals(30.0, PredictionModule.sma(prices, 1), 0.001);
    }

    // ----------------------------------------------------------------
    // ema()
    // ----------------------------------------------------------------

    @Test
    @DisplayName("ema: single-element list returns that element")
    void ema_singleElement() {
        assertEquals(42.0, PredictionModule.ema(List.of(42.0), 1), 0.001);
    }

    @Test
    @DisplayName("ema: empty list returns 0")
    void ema_emptyList() {
        assertEquals(0.0, PredictionModule.ema(Collections.emptyList(), 5), 0.001);
    }

    @Test
    @DisplayName("ema: later prices weighted more heavily than earlier ones")
    void ema_recentBias() {
        // All same except last price is much higher — EMA should be above simple average
        List<Double> prices = List.of(100.0, 100.0, 100.0, 100.0, 200.0);
        double ema = PredictionModule.ema(prices, 5);
        double sma = PredictionModule.sma(prices, 5); // = 120
        assertTrue(ema > sma, "EMA should be higher than SMA when last price spikes");
    }

    // ----------------------------------------------------------------
    // rsi()
    // ----------------------------------------------------------------

    @Test
    @DisplayName("rsi: all gains → returns 100")
    void rsi_allGains_returns100() {
        List<Double> prices = risingPrices(15, 100, 1);
        double rsi = PredictionModule.rsi(prices, 14);
        assertEquals(100.0, rsi, 0.001);
    }

    @Test
    @DisplayName("rsi: all losses → returns 0")
    void rsi_allLosses_returns0() {
        List<Double> prices = fallingPrices(15, 200, 1);
        double rsi = PredictionModule.rsi(prices, 14);
        assertEquals(0.0, rsi, 0.001);
    }

    @Test
    @DisplayName("rsi: result always within [0, 100]")
    void rsi_alwaysInRange() {
        List<Double> prices = List.of(100.0, 102.0, 98.0, 105.0, 97.0, 110.0, 95.0, 108.0);
        double rsi = PredictionModule.rsi(prices, 7);
        assertTrue(rsi >= 0 && rsi <= 100, "RSI out of range: " + rsi);
    }

    @Test
    @DisplayName("rsi: single price → returns 50 (neutral default)")
    void rsi_singlePrice_returnsNeutral() {
        assertEquals(50.0, PredictionModule.rsi(List.of(100.0), 5), 0.001);
    }

    // ----------------------------------------------------------------
    // Utils.capitalize()
    // ----------------------------------------------------------------

    @Test
    @DisplayName("capitalize: first letter uppercased")
    void capitalize_uppercasesFirst() {
        assertEquals("Apple", Utils.capitalize("apple"));
    }

    @Test
    @DisplayName("capitalize: already capitalised string unchanged")
    void capitalize_alreadyCapitalised() {
        assertEquals("Microsoft", Utils.capitalize("Microsoft"));
    }

    @Test
    @DisplayName("capitalize: empty string returns empty string")
    void capitalize_emptyString() {
        assertEquals("", Utils.capitalize(""));
    }

    @Test
    @DisplayName("capitalize: null returns null")
    void capitalize_null() {
        assertNull(Utils.capitalize(null));
    }
}
