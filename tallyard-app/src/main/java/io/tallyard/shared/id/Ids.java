package io.tallyard.shared.id;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.UUID;

/**
 * UUIDv7 generator (RFC 9562): 48-bit Unix millisecond timestamp followed by random bits. Time
 * ordering keeps B-tree indexes append-friendly, which matters for the high-insert tables
 * ({@code usage_event}, {@code outbox_event}).
 */
public final class Ids {

    private static final SecureRandom RANDOM = new SecureRandom();

    private Ids() {}

    public static UUID newId() {
        return newId(System.currentTimeMillis());
    }

    public static UUID newId(Clock clock) {
        return newId(clock.millis());
    }

    /** Deterministic-timestamp variant used by tests and backfills. */
    public static UUID newId(long epochMillis) {
        long randA = RANDOM.nextLong() & 0x0FFFL;
        long msb = (epochMillis << 16) | 0x7000L | randA;
        long lsb = (RANDOM.nextLong() & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
        return new UUID(msb, lsb);
    }

    /** Extracts the embedded millisecond timestamp of a UUIDv7. */
    public static long timestampMillis(UUID id) {
        return id.getMostSignificantBits() >>> 16;
    }
}
