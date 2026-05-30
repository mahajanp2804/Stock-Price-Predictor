package com.capstone.model;

import java.util.ArrayList;
import java.util.List;

public class Stock {
    private final String name;
    private final List<Double> priceHistory;

    public Stock(String name, List<Double> priceHistory) {
        this.name = name;
        this.priceHistory = new ArrayList<>(priceHistory);
    }

    public String getName() { return name; }

    public List<Double> getPriceHistory() { return new ArrayList<>(priceHistory); }

    public double getLatestPrice() { return priceHistory.get(priceHistory.size() - 1); }

    public void addPrice(double price) { priceHistory.add(price); }
}
