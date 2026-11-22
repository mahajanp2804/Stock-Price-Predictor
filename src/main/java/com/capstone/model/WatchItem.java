package com.capstone.model;

import java.util.List;

/** A stock on a user's watchlist. */
public record WatchItem(String symbol, String companyName) {

    /** The stocks every new user starts with. */
    public static final List<WatchItem> DEFAULTS = List.of(
        new WatchItem("AAPL",  "Apple Inc"),
        new WatchItem("MSFT",  "Microsoft Corp"),
        new WatchItem("AMZN",  "Amazon.com Inc"),
        new WatchItem("GOOGL", "Alphabet Inc Class A"),
        new WatchItem("TSLA",  "Tesla Inc"));
}
