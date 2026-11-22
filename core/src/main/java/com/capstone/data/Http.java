package com.capstone.data;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/** Minimal shared HTTP GET helper for the price providers. */
final class Http {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(8))
        .build();

    private Http() {}

    /** GETs base + URL-encoded query params and returns the body. Throws on non-2xx. */
    static String get(String base, Map<String, String> params) throws IOException, InterruptedException {
        String query = params.entrySet().stream()
            .map(e -> enc(e.getKey()) + "=" + enc(e.getValue()))
            .collect(Collectors.joining("&"));
        HttpRequest req = HttpRequest.newBuilder(URI.create(base + "?" + query))
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build();
        HttpResponse<String> res = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) {
            throw new IOException("HTTP " + res.statusCode() + ": " + truncate(res.body()));
        }
        return res.body();
    }

    static Map<String, String> params(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }

    private static String truncate(String s) { return s == null ? "" : s.length() > 200 ? s.substring(0, 200) + "…" : s; }
}
