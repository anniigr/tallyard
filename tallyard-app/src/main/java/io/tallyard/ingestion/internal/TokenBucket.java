package io.tallyard.ingestion.internal;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Lock-free token bucket: {@code ratePerSecond} tokens refill continuously, capacity equals one
 * second of tokens (bursts up to one second are allowed). Returns the wait time when a request is
 * refused so the API can set {@code Retry-After}. Per-node only, by design: ingest rate limiting
 * protects the node, not a global quota.
 */
final class TokenBucket {

    private final double ratePerSecond;
    private final long capacityNanos;
    private final AtomicLong deficitNanos = new AtomicLong(0);
    private final NanoClock clock;

    interface NanoClock {
        long nanoTime();
    }

    TokenBucket(int ratePerSecond) {
        this(ratePerSecond, System::nanoTime);
    }

    TokenBucket(int ratePerSecond, NanoClock clock) {
        if (ratePerSecond <= 0) {
            throw new IllegalArgumentException("rate must be positive");
        }
        this.ratePerSecond = ratePerSecond;
        this.capacityNanos = TimeUnit.SECONDS.toNanos(1);
        this.clock = clock;
    }

    /** @return 0 if {@code tokens} were consumed, otherwise the nanoseconds until they would be available. */
    long tryConsume(int tokens) {
        long cost = (long) (tokens / ratePerSecond * TimeUnit.SECONDS.toNanos(1));
        while (true) {
            long now = clock.nanoTime();
            long current = deficitNanos.get();
            // GCRA: "deficit" is the theoretical time at which everything consumed so far has been
            // paid back at the refill rate. A request is admitted while the new deficit stays within
            // one capacity (one second of tokens) ahead of now; otherwise it must wait the excess.
            long base = Math.max(current, now);
            long next = base + cost;
            if (next - now > capacityNanos) {
                return next - now - capacityNanos;
            }
            if (deficitNanos.compareAndSet(current, next)) {
                return 0;
            }
        }
    }
}
