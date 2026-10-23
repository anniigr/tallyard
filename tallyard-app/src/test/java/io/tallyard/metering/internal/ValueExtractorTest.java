package io.tallyard.metering.internal;

import static org.assertj.core.api.Assertions.assertThat;

import io.tallyard.catalog.Aggregation;
import io.tallyard.catalog.MeterView;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ValueExtractorTest {

    static MeterView meter(Aggregation agg, String property) {
        return new MeterView(
                UUID.randomUUID(), UUID.randomUUID(), "m", "M", "t", agg, property, "u", Duration.ofDays(35));
    }

    @Test
    void countNeedsNoValue() {
        assertThat(ValueExtractor.extract(meter(Aggregation.COUNT, null), Map.of())
                        .orElseThrow()
                        .numeric())
                .isEqualByComparingTo("1");
    }

    @Test
    void numericValuesAcceptPlainDecimalsOnly() {
        MeterView sum = meter(Aggregation.SUM, "v");
        assertThat(ValueExtractor.extract(sum, Map.of("v", 12)).orElseThrow().numeric())
                .isEqualByComparingTo("12");
        assertThat(ValueExtractor.extract(sum, Map.of("v", "12.5"))
                        .orElseThrow()
                        .numeric())
                .isEqualByComparingTo("12.5");
        assertThat(ValueExtractor.extract(sum, Map.of("v", 2.5)).orElseThrow().numeric())
                .isEqualByComparingTo("2.5");
        assertThat(ValueExtractor.extract(sum, Map.of("v", new BigDecimal("1.000")))
                        .orElseThrow()
                        .numeric())
                .isEqualByComparingTo("1");
        assertThat(ValueExtractor.extract(sum, Map.of("v", "1e3"))).isEmpty(); // mirrors the SQL regex
        assertThat(ValueExtractor.extract(sum, Map.of("v", "abc"))).isEmpty();
        assertThat(ValueExtractor.extract(sum, Map.of("v", "1.1234567"))).isEmpty(); // more than 6 decimals
        assertThat(ValueExtractor.extract(sum, Map.of("other", 1))).isEmpty();
        assertThat(ValueExtractor.extract(sum, null)).isEmpty();
    }

    @Test
    void uniqueCountTakesScalarsAsText() {
        MeterView uniq = meter(Aggregation.UNIQUE_COUNT, "id");
        assertThat(ValueExtractor.extract(uniq, Map.of("id", "dev-1"))
                        .orElseThrow()
                        .text())
                .isEqualTo("dev-1");
        assertThat(ValueExtractor.extract(uniq, Map.of("id", 42)).orElseThrow().text())
                .isEqualTo("42");
        assertThat(ValueExtractor.extract(uniq, Map.of("id", Map.of("nested", 1))))
                .isEmpty();
    }
}
