package com.capstone.data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterTest {

    /** A fake clock that only moves when the limiter sleeps. */
    private long now = 0;
    private final List<Long> sleeps = new ArrayList<>();

    private RateLimiter limiter(int permits, long windowNanos) {
        return new RateLimiter(permits, Duration.ofNanos(windowNanos), () -> now, nanos -> { sleeps.add(nanos); now += nanos; });
    }

    @Test
    @DisplayName("the first N calls go straight through; the next waits for the window to pass")
    void waitsWhenFull() throws Exception {
        RateLimiter limit = limiter(3, 1000);
        limit.acquire(); now += 10;
        limit.acquire(); now += 10;
        limit.acquire();
        assertTrue(sleeps.isEmpty());
        limit.acquire();                       // 4th call at t=20 must wait until t=1000
        assertEquals(List.of(980L), sleeps);
        assertEquals(1000, now);
    }

    @Test
    @DisplayName("calls spread over more than the window never wait")
    void noWaitWhenSpread() throws Exception {
        RateLimiter limit = limiter(2, 100);
        for (int i = 0; i < 10; i++) { limit.acquire(); now += 60; }
        assertTrue(sleeps.isEmpty());
    }
}
