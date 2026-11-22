package com.capstone.web;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Login sessions: random bearer tokens held in memory. Restarting the server logs
 * everyone out, which is fine for a single-instance app.
 */
@Component
class SessionStore {

    static final Duration TTL = Duration.ofHours(12);

    record Session(int userId, String username, Instant expiresAt) {}

    private final SecureRandom random = new SecureRandom();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    String create(int userId, String username) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessions.put(token, new Session(userId, username, Instant.now().plus(TTL)));
        return token;
    }

    Optional<Session> find(String token) {
        if (token == null) return Optional.empty();
        Session s = sessions.get(token);
        if (s == null) return Optional.empty();
        if (s.expiresAt().isBefore(Instant.now())) { sessions.remove(token); return Optional.empty(); }
        return Optional.of(s);
    }

    void remove(String token) {
        if (token != null) sessions.remove(token);
    }

    @Scheduled(fixedDelay = 3_600_000)
    void removeExpired() {
        Instant now = Instant.now();
        sessions.values().removeIf(s -> s.expiresAt().isBefore(now));
    }
}
