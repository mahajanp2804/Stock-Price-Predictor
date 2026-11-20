package com.capstone.ui;

import com.capstone.model.PricePoint;
import com.capstone.model.Stock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class PredictPricesPanelTest {

    private static Stock stock(List<Double> closes) {
        List<PricePoint> h = new ArrayList<>();
        LocalDate d = LocalDate.of(2026, 1, 1);
        for (double c : closes) { h.add(new PricePoint(d, c)); d = d.plusDays(1); }
        return new Stock("Test", h);
    }

    @Test
    @DisplayName("on a steady trend, drift is picked as most accurate and forecasts the line")
    void trendPicksDrift() {
        List<Double> closes = new ArrayList<>();
        for (int i = 0; i < 100; i++) closes.add(100 + i * 1.0);
        String report = PredictPricesPanel.report(stock(closes), 3, "Most accurate (backtested)");
        assertTrue(report.contains("Most accurate: Average drift"), report);
        assertTrue(report.contains("=== Forecast: Average drift ==="), report);
        assertTrue(report.contains("Day +3    $202.00"), report);
        assertTrue(report.contains("Replayed 68 past forecasts"), report);   // 100 - 30 - 3 + 1
    }

    @Test
    @DisplayName("on a random walk, the report says no model beat today's price")
    void randomWalkSaysSo() {
        Random rnd = new Random(42);
        List<Double> closes = new ArrayList<>(List.of(100.0));
        for (int i = 1; i < 300; i++) closes.add(closes.get(i - 1) * (1 + rnd.nextGaussian() * 0.015));
        String report = PredictPricesPanel.report(stock(closes), 5, "Most accurate (backtested)");
        assertTrue(report.contains("No model beat simply assuming the price stays where it is"), report);
        assertTrue(report.contains("=== Forecast: Today's price ==="), report);
    }

    @Test
    @DisplayName("a specific model can be chosen for the forecast")
    void chosenModel() {
        List<Double> closes = new ArrayList<>();
        for (int i = 0; i < 100; i++) closes.add(100 + i * 1.0);
        String report = PredictPricesPanel.report(stock(closes), 2, "Regression + EMA + RSI");
        assertTrue(report.contains("=== Forecast: Regression + EMA + RSI ==="), report);
    }

    @Test
    @DisplayName("too little history explains what's needed instead of failing")
    void tooShort() {
        String report = PredictPricesPanel.report(stock(List.of(100.0, 101.0, 102.0)), 5, "Most accurate (backtested)");
        assertTrue(report.contains("Not enough history to test the models 5 day(s) ahead (need at least 35 days)"), report);
    }
}
