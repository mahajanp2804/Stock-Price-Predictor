package com.capstone.web;

import com.capstone.db.DBConnection;
import com.capstone.db.PriceCache;
import com.capstone.model.PricePoint;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.*;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs the real server on a random port against TEST_DB_URL and calls the API over HTTP.
 * Price history for AAPL is seeded into the DB cache, so no API keys are needed.
 * Skipped when TEST_DB_URL isn't set; empties every table, so never point it at a real database.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WebApiDbTest {

    static final String URL = System.getenv("TEST_DB_URL");
    static ConfigurableApplicationContext app;
    static String base;
    static String token;
    static final HttpClient http = HttpClient.newHttpClient();

    record Res(int status, String body) {
        JSONObject json() { return new JSONObject(body); }
        JSONArray array() { return new JSONArray(body); }
    }

    @BeforeAll
    static void start() throws Exception {
        Assumptions.assumeTrue(URL != null && !URL.isBlank(), "TEST_DB_URL not set — skipping web API tests");
        DBConnection.configure(URL, env("TEST_DB_USER", "root"), env("TEST_DB_PASSWORD", ""));
        try (Connection c = DBConnection.get(); Statement s = c.createStatement()) {
            for (String t : new String[]{"orders", "watchlist", "transactions", "portfolio", "users", "stocks", "price_history", "history_fetch_log"}) {
                s.executeUpdate("DELETE FROM " + t);
            }
        }
        List<PricePoint> history = new ArrayList<>();
        LocalDate d = LocalDate.of(2026, 6, 1);
        for (int i = 0; i < 120; i++) history.add(new PricePoint(d.plusDays(i), 100 + i * 0.5 + (i % 3)));
        new PriceCache().saveHistory("AAPL", history);   // fresh cache → no provider calls; last close 161.5
        List<PricePoint> flat = new ArrayList<>();
        for (int i = 0; i < 120; i++) flat.add(new PricePoint(d.plusDays(i), 400));
        new PriceCache().saveHistory("MSFT", flat);

        app = SpringApplication.run(WebApp.class, "--server.port=0");
        base = "http://localhost:" + app.getEnvironment().getProperty("local.server.port");
    }

    @AfterAll
    static void stop() {
        if (app != null) app.close();
        DBConnection.shutdown();
    }

    // ----------------------------------------------------------------

    @Test @Order(1)
    void healthNeedsNoLogin() throws Exception {
        Res r = get("/api/health", null);
        assertEquals(200, r.status());
        assertEquals("up", r.json().getString("database"));
    }

    @Test @Order(1)
    void frontEndServedWithSecurityHeaders() throws Exception {
        HttpResponse<String> r = http.send(req("/", null).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("<title>Stock Price Predictor</title>"));
        String csp = r.headers().firstValue("Content-Security-Policy").orElse("");
        assertTrue(csp.contains("default-src 'self'") && csp.contains("frame-ancestors 'none'"), csp);
        assertFalse(csp.contains("unsafe-inline"), csp);
        assertEquals("nosniff", r.headers().firstValue("X-Content-Type-Options").orElse(""));
        assertEquals(200, get("/app.js", null).status());
    }

    @Test @Order(2)
    void unknownPathsAndMethods() throws Exception {
        assertEquals(404, get("/no-such-page", null).status());
        assertEquals(405, delete("/api/health", null).status());
    }

    @Test @Order(2)
    void apiRequiresLogin() throws Exception {
        assertEquals(401, get("/api/me", null).status());
        assertEquals(401, get("/api/me", "not-a-real-token").status());
    }

    @Test @Order(3)
    void registerAndLogin() throws Exception {
        assertEquals(400, post("/api/auth/register", "{\"username\":\"web\",\"password\":\"123\"}", null).status());
        assertEquals(201, post("/api/auth/register", "{\"username\":\"web\",\"password\":\"password1\"}", null).status());
        assertEquals(409, post("/api/auth/register", "{\"username\":\"web\",\"password\":\"password1\"}", null).status());
        assertEquals(401, post("/api/auth/login", "{\"username\":\"web\",\"password\":\"wrong-one\"}", null).status());
        Res login = post("/api/auth/login", "{\"username\":\"web\",\"password\":\"password1\"}", null);
        assertEquals(200, login.status());
        token = login.json().getString("token");
        assertTrue(token.length() >= 40);
        Res me = get("/api/me", token);
        assertEquals("web", me.json().getString("username"));
        assertEquals(10000.0, me.json().getDouble("balance"), 1e-9);
    }

    @Test @Order(4)
    void watchlistHasDefaultsAndCachedPrices() throws Exception {
        JSONArray list = get("/api/watchlist", token).array();
        assertEquals(5, list.length());
        JSONObject aapl = find(list, "AAPL");
        assertTrue(aapl.getBoolean("available"));
        assertEquals(100 + 119 * 0.5 + (119 % 3), aapl.getDouble("price"), 1e-9);
        assertFalse(find(list, "TSLA").getBoolean("available"), "no cache and no API keys → unavailable");
    }

    @Test @Order(5)
    void historyAndForecast() throws Exception {
        JSONArray month = get("/api/stocks/AAPL/history?period=1M", token).array();
        JSONArray all = get("/api/stocks/AAPL/history?period=ALL", token).array();
        assertEquals(120, all.length());
        assertTrue(month.length() > 25 && month.length() < 35, "1M = " + month.length());
        assertEquals(400, get("/api/stocks/AAPL/history?period=2W", token).status());
        assertEquals(404, get("/api/stocks/NVDA/history", token).status(), "not on watchlist");

        Res f = get("/api/stocks/AAPL/forecast?days=5", token);
        assertEquals(200, f.status());
        JSONObject fc = f.json();
        assertEquals(3, fc.getJSONArray("models").length());
        assertEquals(5, fc.getJSONArray("forecast").length());
        JSONObject day1 = fc.getJSONArray("forecast").getJSONObject(0);
        assertTrue(day1.getDouble("low") <= day1.getDouble("price") || day1.getDouble("price") <= day1.getDouble("high"));
        assertEquals(400, get("/api/stocks/AAPL/forecast?days=0", token).status());
    }

    @Test @Order(6)
    void trading() throws Exception {
        Res buy = post("/api/trades", "{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":10}", token);
        assertEquals(200, buy.status(), buy.body());
        assertTrue(buy.json().getBoolean("success"));
        double price = buy.json().getDouble("price");
        assertEquals(10000 - Math.round(price * 10 * 100) / 100.0, buy.json().getDouble("balance"), 0.005);

        assertEquals(422, post("/api/trades", "{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":100000}", token).status());
        assertEquals(422, post("/api/trades", "{\"symbol\":\"AAPL\",\"side\":\"SELL\",\"quantity\":11}", token).status());
        assertEquals(400, post("/api/trades", "{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":0}", token).status());
        assertEquals(400, post("/api/trades", "{\"symbol\":\"AAPL\",\"side\":\"HOLD\",\"quantity\":1}", token).status());
        assertEquals(400, post("/api/trades", "not json", token).status());

        Res sell = post("/api/trades", "{\"symbol\":\"AAPL\",\"side\":\"SELL\",\"quantity\":4}", token);
        assertEquals(200, sell.status(), sell.body());

        JSONArray holdings = get("/api/portfolio", token).array();
        assertEquals(1, holdings.length());
        assertEquals(6, holdings.getJSONObject(0).getInt("quantity"));

        JSONArray txns = get("/api/transactions", token).array();
        assertEquals(2, txns.length());
        assertEquals(0.0, txns.getJSONObject(0).getDouble("realizedPnL"), 1e-9, "sold at the price it was bought");
        assertTrue(txns.getJSONObject(1).isNull("realizedPnL"), "buys have no realised P&L");

        JSONObject report = get("/api/portfolio/report", token).json();
        JSONObject s = report.getJSONObject("summary");
        assertEquals(10000.0, s.getDouble("startingValue"), 1e-9);
        assertEquals(s.getDouble("cash") + s.getDouble("holdingsValue"), s.getDouble("total"), 0.005);
        assertEquals(0.0, s.getDouble("totalReturn"), 0.005);
        JSONArray values = report.getJSONArray("history");
        assertTrue(values.length() >= 1);
        assertEquals(s.getDouble("total"), values.getJSONObject(values.length() - 1).getDouble("value"), 0.005);
    }

    @Test @Order(7)
    void limitAndStopOrders() throws Exception {
        Res open = post("/api/orders", "{\"symbol\":\"MSFT\",\"side\":\"BUY\",\"type\":\"LIMIT\",\"quantity\":1,\"triggerPrice\":300}", token);
        assertEquals(201, open.status(), open.body());
        assertEquals("OPEN", open.json().getString("status"));
        int openId = open.json().getInt("id");
        assertEquals(409, delete("/api/watchlist/MSFT", token).status(), "open order blocks removal");

        Res filled = post("/api/orders", "{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"type\":\"LIMIT\",\"quantity\":2,\"triggerPrice\":500}", token);
        assertEquals(201, filled.status(), filled.body());
        assertEquals("FILLED", filled.json().getString("status"), "161.5 is below the 500 limit → fills at once");
        assertEquals(161.5, filled.json().getDouble("fillPrice"), 1e-9);

        Res rejected = post("/api/orders", "{\"symbol\":\"MSFT\",\"side\":\"BUY\",\"type\":\"STOP\",\"quantity\":100,\"triggerPrice\":100}", token);
        assertEquals("REJECTED", rejected.json().getString("status"));
        assertTrue(rejected.json().getString("message").startsWith("Rejected: Insufficient balance"), rejected.body());

        assertEquals(400, post("/api/orders", "{\"symbol\":\"MSFT\",\"side\":\"BUY\",\"type\":\"LIMIT\",\"quantity\":0,\"triggerPrice\":300}", token).status());
        assertEquals(400, post("/api/orders", "{\"symbol\":\"MSFT\",\"side\":\"BUY\",\"type\":\"MARKET\",\"quantity\":1,\"triggerPrice\":300}", token).status());
        assertEquals(400, post("/api/orders", "{\"symbol\":\"MSFT\",\"side\":\"HOLD\",\"type\":\"LIMIT\",\"quantity\":1,\"triggerPrice\":300}", token).status());
        assertEquals(400, post("/api/orders", "{\"symbol\":\"MSFT\",\"side\":\"BUY\",\"type\":\"LIMIT\",\"quantity\":1}", token).status());
        assertEquals(404, post("/api/orders", "{\"symbol\":\"NVDA\",\"side\":\"BUY\",\"type\":\"LIMIT\",\"quantity\":1,\"triggerPrice\":300}", token).status());

        JSONArray list = get("/api/orders", token).array();
        assertEquals(3, list.length());
        assertEquals(openId, list.getJSONObject(0).getInt("id"), "open orders first");

        assertEquals(204, delete("/api/orders/" + openId, token).status());
        assertEquals(404, delete("/api/orders/" + openId, token).status(), "already cancelled");
        assertEquals(404, delete("/api/orders/999999", token).status());
        assertEquals(204, delete("/api/watchlist/MSFT", token).status());
    }

    @Test @Order(8)
    void watchlistChanges() throws Exception {
        assertEquals(409, delete("/api/watchlist/AAPL", token).status(), "shares still held");
        assertEquals(204, delete("/api/watchlist/TSLA", token).status());
        assertEquals(400, post("/api/watchlist", "{\"symbol\":\"bad symbol!\"}", token).status());
        assertEquals(422, post("/api/watchlist", "{\"symbol\":\"NOPE\",\"name\":\"No Data Inc\"}", token).status(), "no prices → not added");
        assertEquals(3, get("/api/watchlist", token).array().length());   // MSFT and TSLA removed
    }

    @Test @Order(9)
    void logout() throws Exception {
        assertEquals(204, post("/api/auth/logout", "", token).status());
        assertEquals(401, get("/api/me", token).status());
    }

    // ----------------------------------------------------------------

    private static JSONObject find(JSONArray list, String symbol) {
        for (int i = 0; i < list.length(); i++) if (list.getJSONObject(i).getString("symbol").equals(symbol)) return list.getJSONObject(i);
        throw new AssertionError(symbol + " missing");
    }

    private static Res get(String path, String token) throws Exception { return send(req(path, token).GET()); }
    private static Res delete(String path, String token) throws Exception { return send(req(path, token).DELETE()); }
    private static Res post(String path, String body, String token) throws Exception {
        return send(req(path, token).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)));
    }

    private static HttpRequest.Builder req(String path, String token) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path));
        if (token != null) b.header("Authorization", "Bearer " + token);
        return b;
    }

    private static Res send(HttpRequest.Builder b) throws Exception {
        HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        return new Res(r.statusCode(), r.body());
    }

    private static String env(String name, String fallback) {
        String v = System.getenv(name);
        return v == null ? fallback : v;
    }
}
