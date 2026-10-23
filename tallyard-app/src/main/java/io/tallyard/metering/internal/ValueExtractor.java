package io.tallyard.metering.internal;

import io.tallyard.catalog.Aggregation;
import io.tallyard.catalog.MeterView;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Reads the meter's value property out of an event. The accepted numeric grammar is deliberately
 * the plain-decimal subset {@code -?[0-9]+(\.[0-9]+)?} and is mirrored by the SQL regex used when
 * replaying from the event log, so Java and PostgreSQL always agree on which events count.
 */
final class ValueExtractor {

    static final Pattern PLAIN_DECIMAL = Pattern.compile("^-?[0-9]{1,19}(\\.[0-9]{1,6})?$");

    private ValueExtractor() {}

    record Value(BigDecimal numeric, String text) {}

    /** Empty means the value is missing or malformed for this meter. */
    static Optional<Value> extract(MeterView meter, Map<String, Object> properties) {
        if (meter.aggregation() == Aggregation.COUNT) {
            return Optional.of(new Value(BigDecimal.ONE, null));
        }
        Object raw = properties == null ? null : properties.get(meter.valueProperty());
        if (raw == null) {
            return Optional.empty();
        }
        if (meter.aggregation() == Aggregation.UNIQUE_COUNT) {
            return (raw instanceof String || raw instanceof Number || raw instanceof Boolean)
                    ? Optional.of(new Value(null, String.valueOf(raw)))
                    : Optional.empty();
        }
        String text = raw instanceof Number n ? numberToPlain(n) : raw instanceof String s ? s.trim() : null;
        if (text == null || !PLAIN_DECIMAL.matcher(text).matches()) {
            return Optional.empty();
        }
        return Optional.of(new Value(new BigDecimal(text), text));
    }

    private static String numberToPlain(Number n) {
        if (n instanceof BigDecimal bd) {
            return bd.stripTrailingZeros().toPlainString();
        }
        if (n instanceof Double || n instanceof Float) {
            return BigDecimal.valueOf(n.doubleValue()).stripTrailingZeros().toPlainString();
        }
        return n.toString();
    }
}
