package io.tallyard.ingestion.internal;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Semantic checks that Bean Validation cannot express: future timestamps beyond the allowed skew,
 * oversized or deeply nested property maps. Age checks (acceptance window) are deliberately NOT
 * done here: they depend on the meter and on period state, which the metering module owns.
 */
final class EventValidator {

    static final int MAX_PROPERTY_DEPTH = 3;
    static final int MAX_STRING_LENGTH = 1024;

    private final Clock clock;
    private final Duration maxFutureSkew;

    EventValidator(Clock clock, Duration maxFutureSkew) {
        this.clock = clock;
        this.maxFutureSkew = maxFutureSkew;
    }

    List<String> validate(IncomingEvent event) {
        List<String> errors = new ArrayList<>(2);
        if (event.occurredAt().isAfter(clock.instant().plus(maxFutureSkew))) {
            errors.add("occurredAt is more than " + maxFutureSkew + " in the future");
        }
        if (event.properties() != null) {
            checkDepth(event.properties(), 1, errors);
        }
        return errors;
    }

    private static void checkDepth(Object value, int depth, List<String> errors) {
        if (depth > MAX_PROPERTY_DEPTH) {
            errors.add("properties nested deeper than " + MAX_PROPERTY_DEPTH + " levels");
            return;
        }
        switch (value) {
            case Map<?, ?> map -> {
                for (Object v : map.values()) {
                    checkDepth(v, depth + 1, errors);
                    if (!errors.isEmpty()) {
                        return;
                    }
                }
            }
            case List<?> list -> {
                if (list.size() > 100) {
                    errors.add("property arrays are limited to 100 elements");
                    return;
                }
                for (Object v : list) {
                    checkDepth(v, depth + 1, errors);
                    if (!errors.isEmpty()) {
                        return;
                    }
                }
            }
            case String s -> {
                if (s.length() > MAX_STRING_LENGTH) {
                    errors.add("property strings are limited to " + MAX_STRING_LENGTH + " characters");
                }
            }
            default -> {}
        }
    }
}
