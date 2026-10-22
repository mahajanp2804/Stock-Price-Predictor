# Stock Price Predictor

[![CI](https://github.com/mahajanp2804/Stock-Price-Predictor/actions/workflows/ci.yml/badge.svg)](https://github.com/mahajanp2804/Stock-Price-Predictor/actions/workflows/ci.yml)

A Java desktop app for **paper trading** and **short-term price forecasting**. Log in, start with a virtual $10,000, buy and sell real stocks at live market prices, track profit and loss, and see a simple forecast of where each stock may be heading.

> **Disclaimer:** This is an educational project. Forecasts come from simple statistical indicators. They are not financial advice.

## Features

- **Market prices**, loaded at startup, for Apple, Microsoft, Amazon, Google and Tesla. The app tries three data providers in order (Finnhub → Twelve Data → Alpha Vantage) and falls back to the last price saved in MySQL if all three fail.
- **Accounts:** register and log in. Passwords are hashed with BCrypt.
- **Paper trading:** buy and sell against a virtual balance. Each trade runs in a single database transaction.
- **Portfolio:** holdings with average cost, market value and unrealised P&L.
- **Transaction history**, with CSV export of the portfolio and of transactions.
- **Price prediction:** linear regression adjusted by EMA and RSI, forecasting N days ahead.
- **Trend analysis:** 5-day vs 20-day moving-average crossover (Strong Uptrend … Strong Downtrend).
- **Light and dark themes** (FlatLaf).

## Tech stack

Java 17 · Swing + FlatLaf · MySQL 8 + HikariCP · org.json · JUnit 5 · Maven

## Getting started

### Prerequisites

- JDK 17 or newer
- Maven 3.9+
- MySQL 8 running locally
- Free API keys from at least one of [Finnhub](https://finnhub.io), [Twelve Data](https://twelvedata.com) or [Alpha Vantage](https://www.alphavantage.co)

### 1. Create the database

```bash
mysql -u root -p < db/stockdb.sql
```

### 2. Configure

```bash
cp config.example.properties config.properties
```

Fill in your API keys and MySQL credentials in `config.properties`. Git ignores this file, and it is never packaged into the jar.

You can set any value as an environment variable instead, and it takes priority over the file. Upper-case the key and replace `.` with `_`:

| Property | Environment variable |
|---|---|
| `finnhub.key` | `FINNHUB_KEY` |
| `twelvedata.key` | `TWELVEDATA_KEY` |
| `alphavantage.key` | `ALPHAVANTAGE_KEY` |
| `db.url` / `db.user` / `db.password` | `DB_URL` / `DB_USER` / `DB_PASSWORD` |

### 3. Build and run

```bash
mvn clean package
java -jar target/stock-trading-app.jar
```

Run it from the project root so it finds `config.properties`. To run from source without packaging:

```bash
mvn compile exec:java
```

### Run tests

```bash
mvn test
```

## Project structure

```
├── config.example.properties   # config template (copy to config.properties)
├── db/stockdb.sql              # MySQL schema
├── pom.xml
└── src
    ├── main/java/com/capstone
    │   ├── data/        # price providers (Finnhub, Twelve Data, Alpha Vantage) + fallback chain
    │   ├── db/          # config, connection pool, auth, portfolio/trading logic
    │   ├── model/       # Stock, PortfolioEntry
    │   ├── prediction/  # regression, SMA/EMA/RSI, trend detection
    │   └── ui/          # Swing GUI, login dialog, CSV export
    └── test/java/com/capstone
        └── prediction/  # unit tests for the prediction module
```

## How the prediction works

1. Fit a least-squares line to the recent closing prices.
2. Nudge the projection toward the 14-day EMA. The nudge fades the further ahead the forecast goes.
3. Apply a small mean-reversion factor when RSI shows the stock is overbought (> 70) or oversold (< 30).

The trend label compares the 5-day and 20-day simple moving averages. When fewer than 20 prices are available, it uses the overall percentage change instead.

## Roadmap

- [x] Keep secrets out of git; allow environment-variable config
- [x] Standard Maven layout, runnable jar, CI
- [ ] Exact money arithmetic (`BigDecimal` / `DECIMAL`), keep the UI responsive, periodic price refresh
- [ ] Tests for trading logic and data providers
- [ ] Backtest the predictor against a "tomorrow = today" baseline (MAE, RMSE, directional accuracy)
- [ ] Any-ticker search, price charts, watchlist
