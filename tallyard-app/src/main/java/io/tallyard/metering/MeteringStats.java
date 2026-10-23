package io.tallyard.metering;

/** Tenant-level counts from the database (not process counters), so they survive restarts. */
public record MeteringStats(long storedEvents, long adjustments, long rejected) {}
