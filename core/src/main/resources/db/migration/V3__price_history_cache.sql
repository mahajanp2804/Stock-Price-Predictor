-- Cache of daily closing prices, so restarts don't re-download history
-- (Twelve Data's free plan allows 8 requests a minute) and the app still
-- has history when every provider is down.

CREATE TABLE IF NOT EXISTS price_history (
    stock_name VARCHAR(100)  NOT NULL,
    trade_date DATE          NOT NULL,
    close      DECIMAL(15,4) NOT NULL,
    PRIMARY KEY (stock_name, trade_date)
);

-- When each stock's history was last downloaded from a provider
CREATE TABLE IF NOT EXISTS history_fetch_log (
    stock_name VARCHAR(100) PRIMARY KEY,
    fetched_at TIMESTAMP    NOT NULL
);
