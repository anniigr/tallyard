package io.tallyard.metering;

import io.tallyard.catalog.Aggregation;
import java.math.BigDecimal;
import java.util.UUID;

/** Live quantity of one meter for one customer within one billing period. */
public record MeterUsage(
        UUID meterId, String meterCode, String unit, Aggregation aggregation, BigDecimal quantity, long eventCount) {}
