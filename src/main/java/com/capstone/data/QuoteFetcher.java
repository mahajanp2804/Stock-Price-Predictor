package com.capstone.data;

import java.util.OptionalDouble;

/** A provider of the latest (intraday) price for a ticker. */
public interface QuoteFetcher {

    String providerName();

    /** True if an API key is configured for this provider. */
    boolean isConfigured();

    /** Latest traded price, or empty on any error — never throws. */
    OptionalDouble fetchQuote(String symbol);
}
