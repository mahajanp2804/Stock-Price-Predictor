package com.capstone.data;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.LongSupplier;

/** Blocks callers so at most {@code permits} calls start within any {@code window}. Thread-safe. */
final class RateLimiter {

    @FunctionalInterface
    interface Sleeper { void sleep(long nanos) throws InterruptedException; }

    private final int permits;
    private final long windowNanos;
    private final LongSupplier clock;
    private final Sleeper sleeper;
    private final Deque<Long> starts = new ArrayDeque<>();

    RateLimiter(int permits, Duration window) {
        this(permits, window, System::nanoTime, nanos -> Thread.sleep(nanos / 1_000_000, (int) (nanos % 1_000_000)));
    }

    RateLimiter(int permits, Duration window, LongSupplier clock, Sleeper sleeper) {
        this.permits = permits;
        this.windowNanos = window.toNanos();
        this.clock = clock;
        this.sleeper = sleeper;
    }

    /** Waits until a call is allowed, then records it. */
    synchronized void acquire() throws InterruptedException {
        while (true) {
            long now = clock.getAsLong();
            while (!starts.isEmpty() && now - starts.peekFirst() >= windowNanos) starts.pollFirst();
            if (starts.size() < permits) {
                starts.addLast(now);
                return;
            }
            sleeper.sleep(starts.peekFirst() + windowNanos - now);
        }
    }
}
