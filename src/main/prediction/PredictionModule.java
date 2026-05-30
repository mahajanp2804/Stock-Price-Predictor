package com.capstone.prediction;

import com.capstone.model.Stock;
import java.util.*;

public class PredictionModule {

    public static String predictTrend(List<Double> prices) {
        if (prices == null || prices.size() < 2) return "Insufficient data";
        if (prices.size() >= 20) {
            double sma5 = sma(prices, 5), sma20 = sma(prices, 20);
            double diff = (sma5 - sma20) / sma20 * 100;
            if (diff > 1.5) return "Strong Uptrend"; if (diff > 0) return "Uptrend";
            if (diff < -1.5) return "Strong Downtrend"; if (diff < 0) return "Downtrend";
            return "Sideways";
        } else {
            double pct = (prices.get(prices.size()-1) - prices.get(0)) / prices.get(0) * 100;
            if (pct > 2.0) return "Strong Uptrend"; if (pct > 0) return "Uptrend";
            if (pct < -2.0) return "Strong Downtrend"; if (pct < 0) return "Downtrend";
            return "Sideways";
        }
    }

    public static List<Double> predictFuturePrices(List<Double> prices, int daysAhead) {
        if (prices == null || prices.isEmpty() || daysAhead <= 0) return new ArrayList<>();
        int n = prices.size();
        double xMean = (n + 1) / 2.0;
        double yMean = prices.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double num = 0, den = 0;
        for (int i = 0; i < n; i++) { double xi = i+1; num += (xi-xMean)*(prices.get(i)-yMean); den += (xi-xMean)*(xi-xMean); }
        double slope = (den == 0) ? 0 : num/den;
        double intercept = yMean - slope * xMean;
        double ema = ema(prices, Math.min(14, n));
        double emaBias = ema - (slope * n + intercept);
        double rsi = rsi(prices, Math.min(14, n-1));
        double rsiFactor = rsi > 70 ? 0.997 : rsi < 30 ? 1.003 : 1.0;
        List<Double> result = new ArrayList<>();
        for (int i = 1; i <= daysAhead; i++) {
            double lr = slope * (n + i) + intercept;
            result.add(Math.max(0, (lr + emaBias * Math.exp(-0.15 * i)) * Math.pow(rsiFactor, i)));
        }
        return result;
    }

    public static double sma(List<Double> prices, int period) {
        int start = Math.max(0, prices.size() - period);
        return prices.subList(start, prices.size()).stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    public static double ema(List<Double> prices, int period) {
        if (prices.isEmpty()) return 0;
        List<Double> w = prices.subList(Math.max(0, prices.size()-period), prices.size());
        double k = 2.0 / (w.size() + 1), e = w.get(0);
        for (int i = 1; i < w.size(); i++) e = w.get(i) * k + e * (1 - k);
        return e;
    }

    public static double rsi(List<Double> prices, int period) {
        if (prices.size() < 2) return 50;
        int start = Math.max(1, prices.size() - period);
        double gains = 0, losses = 0, count = 0;
        for (int i = start; i < prices.size(); i++) {
            double ch = prices.get(i) - prices.get(i-1);
            if (ch > 0) gains += ch; else losses -= ch; count++;
        }
        if (count == 0 || losses == 0) return gains == 0 ? 50 : 100;
        double rs = (gains/count) / (losses/count);
        return 100 - (100 / (1 + rs));
    }

    @Deprecated public static String capitalize(String str) { return Utils.capitalize(str); }

    public static void displayRecentHistory(Stock stock) {
        List<Double> history = stock.getPriceHistory();
        System.out.println("\nRecent Stock Prices for " + stock.getName() + ":");
        for (int i = 0; i < history.size(); i++) System.out.printf(" Day %d : $%.2f%n", i+1, history.get(i));
    }
}
