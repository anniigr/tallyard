package io.tallyard.shared.money;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Helpers for quantities (not money): fixed scale 6, explicit rounding. */
public final class Decimals {

    public static final int QUANTITY_SCALE = 6;

    private Decimals() {}

    public static BigDecimal quantity(BigDecimal value) {
        return value.setScale(QUANTITY_SCALE, RoundingMode.HALF_EVEN);
    }

    public static BigDecimal quantity(long value) {
        return quantity(BigDecimal.valueOf(value));
    }

    public static BigDecimal max(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    public static BigDecimal min(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    public static boolean isZero(BigDecimal v) {
        return v.signum() == 0;
    }
}
