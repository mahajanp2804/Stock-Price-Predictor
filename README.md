# Stock Price Predictor

[![CI](https://github.com/mahajanp2804/Stock-Price-Predictor/actions/workflows/ci.yml/badge.svg)](https://github.com/mahajanp2804/Stock-Price-Predictor/actions/workflows/ci.yml)

A Java desktop app for **paper trading** and **short-term price forecasting**. Log in, start with a virtual $10,000, buy and sell real stocks at live market prices, track profit and loss, and see a simple forecast of where each stock may be heading.

> **Disclaimer:** This is an educational project. Forecasts come from simple statistical indicators. They are not financial advice.

## Features

- **Live prices** for Apple, Microsoft, Amazon, Google and Tesla. Finnhub quotes refresh every minute and again right before each trade. Daily price history (used for predictions) comes from Twelve Data, falling back to Alpha Vantage, and then to the last price saved in MySQL.
- **Accounts:** register and log in. Passwords are hashed with standard bcrypt (cost 12).
- **Paper trading:** buy and sell against a virtual balance. Money is exact (`DECIMAL` / `BigDecimal`), and each trade locks the user's balance inside one database transaction, so simultaneous trades can't overspend.
- **Responsive UI:** all database and network work runs in the background.
- **Portfolio:** holdings with average cost, market value and unrealised P&L.
- **Transaction history**, with CSV export of the portfolio and of transactions.
- **Price prediction:** linear regression adjusted by EMA and RSI, forecasting N days ahead.
- **Trend analysis:** 5-day vs 20-day moving-average crossover (Strong Uptrend … Strong Downtrend).
- **Light and dark themes** (FlatLaf).

## Tech stack

Java 17 · Swing + FlatLaf · MySQL 8 + HikariCP · bcrypt (at.favre.lib) · SLF4J · org.json · JUnit 5 · Maven

## Getting started

### Prerequisites

- JDK 17 or newer
- Maven 3.9+
- MySQL 8 or newer running locally
- Free API keys: [Twelve Data](https://twelvedata.com) and/or [Alpha Vantage](https://www.alphavantage.co) for price history, and [Finnhub](https://finnhub.io) for live quotes (optional — without it, trades use the last daily close)

### 1. Configure

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

### 2. Build and run

```bash
mvn clean package
java -jar target/stock-trading-app.jar
```

Run it from the project root so it finds `config.properties`. To run from source without packaging:

```bash
mvn compile exec:java
```

**The database sets itself up.** With the default `db.url` (`…/stockdb?createDatabaseIfNotExist=true`), MySQL creates `stockdb` and [Flyway](https://flywaydb.org) applies the schema migrations in `src/main/resources/db/migration` on first connect.

**Upgrading from an older version:** a `stockdb` created by the old `stockdb.sql` script is detected and upgraded automatically, and your data is kept. Accounts created with the old password hashing still log in and are moved to standard bcrypt on their next login.

### Run tests

```bash
mvn test
```

The database tests (`*DbTest`) run only when `TEST_DB_URL` points at a MySQL server; otherwise they're skipped. They empty every table they touch, so use a separate database, never your real one:

```bash
TEST_DB_URL='jdbc:mysql://127.0.0.1:3306/stockdb_test?createDatabaseIfNotExist=true' TEST_DB_USER=root TEST_DB_PASSWORD=yourpassword mvn test
```

CI runs all tests, including the database tests, against MySQL 8.4 on every push.

## Project structure

```
├── config.example.properties   # config template (copy to config.properties)
├── pom.xml
└── src
    ├── main/java/com/capstone
    │   ├── data/        # history providers (Twelve Data, Alpha Vantage), live quotes (Finnhub)
    │   ├── db/          # connection pool + migrations, config, auth + password hashing, trading logic
    │   ├── model/       # Stock, PortfolioEntry, Transaction, Money
    │   ├── prediction/  # regression, SMA/EMA/RSI, trend detection
    │   └── ui/          # main window, one class per tab, login dialog, CSV export
    ├── main/resources/db/migration/   # Flyway schema migrations (V1, V2, …)
    └── test/java/com/capstone         # unit tests + database tests (*DbTest)
```

## How the prediction works

1. Fit a least-squares line to the recent closing prices.
2. Nudge the projection toward the 14-day EMA. The nudge fades the further ahead the forecast goes.
3. Apply a small mean-reversion factor when RSI shows the stock is overbought (> 70) or oversold (< 30).

The trend label compares the 5-day and 20-day simple moving averages. When fewer than 20 prices are available, it uses the overall percentage change instead.

## Roadmap

- [x] Keep secrets out of git; allow environment-variable config
- [x] Standard Maven layout, runnable jar, CI
- [x] Exact money arithmetic, locked balance updates, responsive UI, live price refresh, standard bcrypt, SLF4J logging
- [x] Database tests in CI, Flyway migrations, GUI split into one class per tab
- [ ] Backtest the predictor against a "tomorrow = today" baseline (MAE, RMSE, directional accuracy)
- [ ] Any-ticker search, price charts, watchlist
