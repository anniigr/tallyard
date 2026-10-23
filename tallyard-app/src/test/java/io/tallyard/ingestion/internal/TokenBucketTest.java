package io.tallyard.ingestion.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class TokenBucketTest {

    @Test
    void allowsOneSecondOfBurstThenRefillsAtRate() {
        AtomicLong nanos = new AtomicLong(0);
        TokenBucket bucket = new TokenBucket(1000, nanos::get);
        assertThat(bucket.tryConsume(1000)).isZero(); // full burst
        long wait = bucket.tryConsume(1);
        assertThat(wait).isGreaterThan(0).isLessThanOrEqualTo(TimeUnit.MILLISECONDS.toNanos(1) + 1);
        nanos.addAndGet(TimeUnit.MILLISECONDS.toNanos(500)); // 500 tokens refilled
        assertThat(bucket.tryConsume(500)).isZero();
        assertThat(bucket.tryConsume(1)).isGreaterThan(0);
        nanos.addAndGet(TimeUnit.SECONDS.toNanos(10)); // never exceeds capacity
        assertThat(bucket.tryConsume(1000)).isZero();
        assertThat(bucket.tryConsume(1)).isGreaterThan(0);
    }

    @Test
    void validatorRejectsFutureTimestampsAndDeepNesting() {
        EventValidator validator = new EventValidator(
                Clock.fixed(java.time.Instant.parse("2026-01-01T00:00:00Z"), java.time.ZoneOffset.UTC),
                Duration.ofMinutes(5));
        IncomingEvent future =
                new IncomingEvent("e", "t", "s", java.time.Instant.parse("2026-01-01T00:06:00Z"), Map.of());
        assertThat(validator.validate(future)).hasSize(1);
        IncomingEvent withinSkew =
                new IncomingEvent("e", "t", "s", java.time.Instant.parse("2026-01-01T00:04:00Z"), Map.of());
        assertThat(validator.validate(withinSkew)).isEmpty();
        IncomingEvent deep = new IncomingEvent(
                "e",
                "t",
                "s",
                java.time.Instant.parse("2026-01-01T00:00:00Z"),
                Map.of("a", Map.of("b", Map.of("c", Map.of("d", 1)))));
        assertThat(validator.validate(deep)).anyMatch(m -> m.contains("nested"));
        IncomingEvent longArray = new IncomingEvent(
                "e",
                "t",
                "s",
                java.time.Instant.parse("2026-01-01T00:00:00Z"),
                Map.of("a", java.util.Collections.nCopies(101, 1)));
        assertThat(validator.validate(longArray)).anyMatch(m -> m.contains("100"));
        assertThat(List.of()).isEmpty();
    }
}
