package io.tallyard.billing.internal;

import static org.assertj.core.api.Assertions.assertThat;

import io.tallyard.catalog.BillingInterval;
import io.tallyard.catalog.PlanView;
import io.tallyard.catalog.PlanView.ComponentView;
import io.tallyard.catalog.PlanView.TierView;
import io.tallyard.catalog.PricingModel;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Properties that must hold for any tier table and any quantity. These catch the classic rating
 * bugs (negative amounts, non-monotonic pricing, boundary off-by-ones) far better than examples.
 */
class RatingEnginePropertyTest {

    static final Currency USD = Currency.getInstance("USD");
    static final UUID METER = UUID.randomUUID();

    record Tiers(List<TierView> tiers) {}

    @Provide
    Arbitrary<Tiers> tierTables() {
        Arbitrary<List<BigDecimal>> bounds = Arbitraries.bigDecimals()
                .between(BigDecimal.ONE, new BigDecimal("1000000"))
                .ofScale(0)
                .list()
                .ofMinSize(0)
                .ofMaxSize(5)
                .uniqueElements()
                .map(l -> {
                    List<BigDecimal> s = new ArrayList<>(l);
                    s.sort(null);
                    return s;
                });
        Arbitrary<BigDecimal> price = Arbitraries.bigDecimals()
                .between(BigDecimal.ZERO, new BigDecimal("10"))
                .ofScale(4);
        return bounds.flatMap(b -> price.list().ofSize(b.size() + 1).map(prices -> {
            List<TierView> tiers = new ArrayList<>();
            for (int i = 0; i < b.size(); i++) {
                tiers.add(new TierView(b.get(i), prices.get(i), BigDecimal.ZERO));
            }
            tiers.add(new TierView(null, prices.get(b.size()), BigDecimal.ZERO));
            return new Tiers(tiers);
        }));
    }

    @Provide
    Arbitrary<BigDecimal> quantities() {
        return Arbitraries.bigDecimals()
                .between(BigDecimal.ZERO, new BigDecimal("2000000"))
                .ofScale(2);
    }

    static PlanView plan(PricingModel model, Tiers t, BigDecimal included) {
        ComponentView c = new ComponentView(UUID.randomUUID(), METER, "m", 0, model, included, null, null, t.tiers());
        return new PlanView(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "p",
                "P",
                USD,
                BillingInterval.monthly(),
                0,
                BigDecimal.ZERO,
                List.of(c));
    }

    static BigDecimal rate(PlanView p, BigDecimal q) {
        return RatingEngine.rate(p, Map.of(METER, q), List.of())
                .lines()
                .get(0)
                .amount()
                .amount();
    }

    @Property(tries = 300)
    void graduatedIsNeverNegativeAndMonotonic(
            @ForAll("tierTables") Tiers t, @ForAll("quantities") BigDecimal q, @ForAll("quantities") BigDecimal extra) {
        PlanView p = plan(PricingModel.GRADUATED, t, BigDecimal.ZERO);
        BigDecimal a = rate(p, q);
        BigDecimal b = rate(p, q.add(extra));
        assertThat(a).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(b).isGreaterThanOrEqualTo(a);
    }

    @Property(tries = 300)
    void graduatedEqualsSumOfBandsComputedIndependently(
            @ForAll("tierTables") Tiers t, @ForAll("quantities") BigDecimal q) {
        PlanView p = plan(PricingModel.GRADUATED, t, BigDecimal.ZERO);
        BigDecimal expected = BigDecimal.ZERO;
        BigDecimal lower = BigDecimal.ZERO;
        for (TierView tier : t.tiers()) {
            BigDecimal upper = tier.upTo() == null ? q : tier.upTo().min(q);
            if (upper.compareTo(lower) > 0) {
                expected = expected.add(upper.subtract(lower).multiply(tier.unitPrice()));
            }
            lower = tier.upTo() == null ? lower : tier.upTo();
            if (tier.upTo() != null && q.compareTo(tier.upTo()) <= 0) {
                break;
            }
        }
        assertThat(rate(p, q)).isEqualByComparingTo(expected.setScale(2, java.math.RoundingMode.HALF_EVEN));
    }

    @Property(tries = 300)
    void volumeNeverExceedsGraduatedWhenPricesDecrease(@ForAll("quantities") BigDecimal q) {
        // Strictly decreasing prices: volume pricing (all units at the reached tier) must be ≤ graduated.
        Tiers t = new Tiers(List.of(
                new TierView(new BigDecimal("1000"), new BigDecimal("0.10"), BigDecimal.ZERO),
                new TierView(new BigDecimal("10000"), new BigDecimal("0.05"), BigDecimal.ZERO),
                new TierView(null, new BigDecimal("0.01"), BigDecimal.ZERO)));
        assertThat(rate(plan(PricingModel.VOLUME, t, BigDecimal.ZERO), q))
                .isLessThanOrEqualTo(rate(plan(PricingModel.GRADUATED, t, BigDecimal.ZERO), q));
    }

    @Property(tries = 200)
    void includedUnitsNeverIncreaseTheBill(
            @ForAll("tierTables") Tiers t,
            @ForAll("quantities") BigDecimal q,
            @ForAll("quantities") BigDecimal included) {
        assertThat(rate(plan(PricingModel.GRADUATED, t, included), q))
                .isLessThanOrEqualTo(rate(plan(PricingModel.GRADUATED, t, BigDecimal.ZERO), q));
    }

    @Property(tries = 200)
    void ratingIsDeterministic(@ForAll("tierTables") Tiers t, @ForAll("quantities") BigDecimal q) {
        PlanView p = plan(PricingModel.GRADUATED, t, BigDecimal.ZERO);
        assertThat(RatingEngine.rate(p, Map.of(METER, q), List.of()).hash())
                .isEqualTo(RatingEngine.rate(p, Map.of(METER, q), List.of()).hash());
    }

    @Property(tries = 100)
    void packageChargesCeilOfBillableOverSize(@ForAll("quantities") BigDecimal q) {
        BigDecimal size = new BigDecimal("7");
        ComponentView c = new ComponentView(
                UUID.randomUUID(),
                METER,
                "m",
                0,
                PricingModel.PACKAGE,
                BigDecimal.ZERO,
                size,
                null,
                List.of(new TierView(null, BigDecimal.ZERO, new BigDecimal("3"))));
        PlanView p = new PlanView(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "p",
                "P",
                USD,
                BillingInterval.monthly(),
                0,
                BigDecimal.ZERO,
                List.of(c));
        BigDecimal packages = q.signum() == 0 ? BigDecimal.ZERO : q.divide(size, 0, java.math.RoundingMode.CEILING);
        assertThat(rate(p, q)).isEqualByComparingTo(packages.multiply(new BigDecimal("3")));
    }

    @Property(tries = 50)
    void combinatorSanity(@ForAll("tierTables") Tiers t) {
        assertThat(Combinators.combine(Arbitraries.just(1), Arbitraries.just(2))
                        .as(Integer::sum)
                        .sample())
                .isEqualTo(3);
        assertThat(t.tiers().get(t.tiers().size() - 1).upTo()).isNull();
    }
}
