package io.tallyard.billing.internal;

import static org.assertj.core.api.Assertions.assertThat;

import io.tallyard.billing.UsageSource.AdjustmentView;
import io.tallyard.catalog.BillingInterval;
import io.tallyard.catalog.PlanView;
import io.tallyard.catalog.PlanView.ComponentView;
import io.tallyard.catalog.PlanView.TierView;
import io.tallyard.catalog.PricingModel;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Worked examples for every pricing model; the numbers are the ones documented in the product spec. */
class RatingEngineTest {

    static final Currency USD = Currency.getInstance("USD");
    static final UUID METER = UUID.randomUUID();
    static final UUID COMPONENT = UUID.randomUUID();

    static ComponentView component(PricingModel model, String included, String packageSize, TierView... tiers) {
        return new ComponentView(
                COMPONENT,
                METER,
                "m",
                0,
                model,
                bd(included),
                packageSize == null ? null : bd(packageSize),
                null,
                List.of(tiers));
    }

    static PlanView plan(String minimum, ComponentView... components) {
        return new PlanView(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "p",
                "Plan",
                USD,
                BillingInterval.monthly(),
                0,
                bd(minimum),
                List.of(components));
    }

    static TierView tier(String upTo, String unitPrice, String flat) {
        return new TierView(upTo == null ? null : bd(upTo), bd(unitPrice), bd(flat));
    }

    static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }

    static BigDecimal amount(RatingEngine.Result r, int line) {
        return r.lines().get(line).amount().amount();
    }

    @Nested
    class Graduated {
        // Lago's documented example: 150 units, 0–100 @ 1.00, 100–500 @ 0.80 → 100 + 40 = 140
        @Test
        void pricesEachBandSeparately() {
            PlanView p = plan(
                    "0",
                    component(
                            PricingModel.GRADUATED,
                            "0",
                            null,
                            tier("100", "1.00", "0"),
                            tier("500", "0.80", "0"),
                            tier(null, "0.50", "0")));
            RatingEngine.Result r = RatingEngine.rate(p, Map.of(METER, bd("150")), List.of());
            assertThat(amount(r, 0)).isEqualByComparingTo("140.00");
            assertThat(r.lines().get(0).breakdown().tiers()).hasSize(2);
        }

        @Test
        void includedUnitsAreFreeBeforeTiering() {
            PlanView p = plan(
                    "0",
                    component(
                            PricingModel.GRADUATED, "100", null, tier("1000", "0.01", "0"), tier(null, "0.005", "0")));
            // 1,600 calls: 100 free, 1,000 @ 0.01 = 10.00, 500 @ 0.005 = 2.50 → 12.50
            RatingEngine.Result r = RatingEngine.rate(p, Map.of(METER, bd("1600")), List.of());
            assertThat(amount(r, 0)).isEqualByComparingTo("12.50");
            assertThat(r.lines().get(0).breakdown().billableUnits()).isEqualByComparingTo("1500");
        }

        @Test
        void zeroUsageCostsNothing() {
            PlanView p = plan(
                    "0", component(PricingModel.GRADUATED, "0", null, tier("100", "1", "0"), tier(null, "0.5", "0")));
            assertThat(amount(RatingEngine.rate(p, Map.of(), List.of()), 0)).isEqualByComparingTo("0");
        }

        @Test
        void tierFlatFeeChargedOnEntry() {
            PlanView p = plan(
                    "0",
                    component(PricingModel.GRADUATED, "0", null, tier("10", "0", "5.00"), tier(null, "1", "7.00")));
            assertThat(amount(RatingEngine.rate(p, Map.of(METER, bd("3")), List.of()), 0))
                    .isEqualByComparingTo("5.00");
            assertThat(amount(RatingEngine.rate(p, Map.of(METER, bd("12")), List.of()), 0))
                    .isEqualByComparingTo("14.00");
        }
    }

    @Nested
    class Volume {
        // Same tiers, volume semantics: 150 units all priced at the tier containing 150 → 150 × 0.80 = 120
        @Test
        void pricesAllUnitsAtTheReachedTier() {
            PlanView p = plan(
                    "0",
                    component(
                            PricingModel.VOLUME,
                            "0",
                            null,
                            tier("100", "1.00", "0"),
                            tier("500", "0.80", "0"),
                            tier(null, "0.50", "0")));
            assertThat(amount(RatingEngine.rate(p, Map.of(METER, bd("150")), List.of()), 0))
                    .isEqualByComparingTo("120.00");
            assertThat(amount(RatingEngine.rate(p, Map.of(METER, bd("100")), List.of()), 0))
                    .isEqualByComparingTo("100.00");
            assertThat(amount(RatingEngine.rate(p, Map.of(METER, bd("5000")), List.of()), 0))
                    .isEqualByComparingTo("2500.00");
        }
    }

    @Nested
    class PerUnitAndFlatAndPackage {
        @Test
        void perUnitWithIncludedUnits() {
            PlanView p = plan("0", component(PricingModel.PER_UNIT, "1000000", null, tier(null, "0.000002", "0")));
            // 1,500,000 tokens: 500,000 billable × 0.000002 = 1.00
            assertThat(amount(RatingEngine.rate(p, Map.of(METER, bd("1500000")), List.of()), 0))
                    .isEqualByComparingTo("1.00");
        }

        @Test
        void flatIgnoresQuantity() {
            PlanView p = plan("0", component(PricingModel.FLAT, "0", null, tier(null, "0", "49.00")));
            assertThat(amount(RatingEngine.rate(p, Map.of(METER, bd("123456")), List.of()), 0))
                    .isEqualByComparingTo("49.00");
        }

        @Test
        void packageRoundsUp() {
            PlanView p = plan("0", component(PricingModel.PACKAGE, "0", "10", tier(null, "0", "5.00")));
            assertThat(amount(RatingEngine.rate(p, Map.of(METER, bd("1")), List.of()), 0))
                    .isEqualByComparingTo("5.00");
            assertThat(amount(RatingEngine.rate(p, Map.of(METER, bd("10")), List.of()), 0))
                    .isEqualByComparingTo("5.00");
            assertThat(amount(RatingEngine.rate(p, Map.of(METER, bd("11")), List.of()), 0))
                    .isEqualByComparingTo("10.00");
            assertThat(amount(RatingEngine.rate(p, Map.of(METER, bd("0")), List.of()), 0))
                    .isEqualByComparingTo("0");
        }
    }

    @Nested
    class MinimumCommitmentAndRounding {
        @Test
        void topsUpToTheMinimum() {
            PlanView p = plan("20.00", component(PricingModel.PER_UNIT, "0", null, tier(null, "0.01", "0")));
            RatingEngine.Result r = RatingEngine.rate(p, Map.of(METER, bd("300")), List.of());
            assertThat(amount(r, 0)).isEqualByComparingTo("3.00");
            assertThat(r.lines()).hasSize(2);
            assertThat(r.lines().get(1).kind()).isEqualTo(RatingEngine.LineKind.MINIMUM_COMMITMENT);
            assertThat(amount(r, 1)).isEqualByComparingTo("17.00");
            assertThat(r.subtotal().amount()).isEqualByComparingTo("20.00");
        }

        @Test
        void noTopUpWhenAboveMinimum() {
            PlanView p = plan("20.00", component(PricingModel.PER_UNIT, "0", null, tier(null, "0.01", "0")));
            RatingEngine.Result r = RatingEngine.rate(p, Map.of(METER, bd("5000")), List.of());
            assertThat(r.lines()).hasSize(1);
            assertThat(r.subtotal().amount()).isEqualByComparingTo("50.00");
        }

        @Test
        void linesAreRoundedHalfEvenToMinorUnits() {
            PlanView p = plan("0", component(PricingModel.PER_UNIT, "0", null, tier(null, "0.000002", "0")));
            // 1,234,567 × 0.000002 = 2.469134 → 2.47
            assertThat(amount(RatingEngine.rate(p, Map.of(METER, bd("1234567")), List.of()), 0))
                    .isEqualByComparingTo("2.47");
            // 625,000 × 0.000002 = 1.25 exactly; 1,125,000 → 2.25; banker's rounding on 2.2250 → 2.22
            assertThat(amount(RatingEngine.rate(p, Map.of(METER, bd("1112500")), List.of()), 0))
                    .isEqualByComparingTo("2.22");
        }
    }

    @Nested
    class Adjustments {
        @Test
        void lateUsageIsPricedMarginally() {
            // graduated 0–1000 @ 0.01, then 0.005; period itself used 900, late usage adds 200 → crosses the tier
            // boundary
            PlanView p = plan(
                    "0",
                    component(PricingModel.GRADUATED, "0", null, tier("1000", "0.01", "0"), tier(null, "0.005", "0")));
            AdjustmentView late =
                    new AdjustmentView(UUID.randomUUID(), METER, UUID.randomUUID(), "late-1", bd("200"), Instant.EPOCH);
            RatingEngine.Result r = RatingEngine.rate(p, Map.of(METER, bd("900")), List.of(late));
            assertThat(amount(r, 0)).isEqualByComparingTo("9.00");
            assertThat(r.lines().get(1).kind()).isEqualTo(RatingEngine.LineKind.ADJUSTMENT);
            // 1100 total = 10.00 + 0.50 = 10.50; marginal = 10.50 − 9.00 = 1.50
            assertThat(amount(r, 1)).isEqualByComparingTo("1.50");
            assertThat(r.subtotal().amount()).isEqualByComparingTo("10.50");
        }
    }

    @Nested
    class Determinism {
        @Test
        void sameInputsSameHash() {
            PlanView p = plan(
                    "1",
                    component(
                            PricingModel.GRADUATED, "100", null, tier("1000", "0.01", "0"), tier(null, "0.005", "0")));
            Map<UUID, BigDecimal> q = Map.of(METER, bd("4321"));
            RatingEngine.Result a = RatingEngine.rate(p, q, List.of());
            RatingEngine.Result b = RatingEngine.rate(p, q, List.of());
            assertThat(a.hash()).isEqualTo(b.hash()).hasSize(64);
            assertThat(RatingEngine.rate(p, Map.of(METER, bd("4322")), List.of())
                            .hash())
                    .isNotEqualTo(a.hash());
        }

        @Test
        void quantityScaleDoesNotChangeHash() {
            PlanView p = plan("0", component(PricingModel.PER_UNIT, "0", null, tier(null, "0.01", "0")));
            assertThat(RatingEngine.rate(p, Map.of(METER, bd("10")), List.of()).hash())
                    .isEqualTo(RatingEngine.rate(p, Map.of(METER, bd("10.000000")), List.of())
                            .hash());
        }
    }
}
