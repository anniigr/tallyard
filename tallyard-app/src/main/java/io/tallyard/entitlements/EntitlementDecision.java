package io.tallyard.entitlements;

import java.math.BigDecimal;
import java.time.Instant;

public record EntitlementDecision(
        Decision decision,
        Reason reason,
        String subject,
        String meter,
        BigDecimal requested,
        BigDecimal used,
        BigDecimal limit,
        BigDecimal remaining,
        BigDecimal creditRemaining,
        Instant asOf) {

    public enum Decision {
        ALLOW,
        DENY
    }

    public enum Reason {
        WITHIN_LIMIT,
        NO_LIMIT,
        UNKNOWN_SUBJECT,
        NO_ACTIVE_SUBSCRIPTION,
        NO_OPEN_PERIOD,
        METER_NOT_IN_PLAN,
        LIMIT_EXCEEDED,
        CREDIT_EXHAUSTED
    }
}
