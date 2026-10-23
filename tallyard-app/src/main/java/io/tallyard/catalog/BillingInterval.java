package io.tallyard.catalog;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/** Length of a billing period. Months are calendar months in UTC; other units are fixed durations. */
public record BillingInterval(Unit unit, int length) {

    public enum Unit {
        MINUTES,
        HOURS,
        DAYS,
        MONTHS
    }

    public BillingInterval {
        if (length <= 0) {
            throw new IllegalArgumentException("Interval length must be positive");
        }
    }

    public static BillingInterval monthly() {
        return new BillingInterval(Unit.MONTHS, 1);
    }

    public Instant endOf(Instant start) {
        ZonedDateTime z = start.atZone(ZoneOffset.UTC);
        return switch (unit) {
            case MINUTES -> z.plusMinutes(length).toInstant();
            case HOURS -> z.plusHours(length).toInstant();
            case DAYS -> z.plusDays(length).toInstant();
            case MONTHS -> z.plusMonths(length).toInstant();
        };
    }
}
