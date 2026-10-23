package io.tallyard.billing.internal;

import io.tallyard.billing.UsageSource;
import io.tallyard.catalog.PlanView;
import io.tallyard.catalog.PlanView.ComponentView;
import io.tallyard.catalog.PlanView.TierView;
import io.tallyard.shared.money.Decimals;
import io.tallyard.shared.money.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pure, deterministic rating. No clock, no I/O, no randomness: the same plan and quantities always
 * produce the same lines and the same hash, which is what makes {@code replay} a meaningful proof.
 *
 * <p>Money is exact to six decimals during computation; each line is rounded to the currency's minor
 * unit with HALF_EVEN, and the subtotal is the sum of the rounded lines (what a customer would add up
 * on paper).
 */
final class RatingEngine {

    enum LineKind {
        USAGE,
        ADJUSTMENT,
        MINIMUM_COMMITMENT
    }

    record TierBreakdown(
            int tier,
            BigDecimal from,
            BigDecimal to,
            BigDecimal units,
            BigDecimal unitPrice,
            BigDecimal flatAmount,
            BigDecimal amount) {}

    record Breakdown(
            String pricingModel,
            BigDecimal quantity,
            BigDecimal includedUnits,
            BigDecimal billableUnits,
            BigDecimal packageSize,
            BigDecimal packages,
            List<TierBreakdown> tiers,
            BigDecimal baseQuantity,
            BigDecimal baseAmount) {}

    record Line(
            LineKind kind,
            UUID componentId,
            UUID meterId,
            String description,
            BigDecimal quantity,
            Money amount,
            Breakdown breakdown) {}

    record Result(List<Line> lines, Money subtotal, String hash) {}

    private RatingEngine() {}

    /**
     * @param quantities quantity per meter id for the period (already aggregated)
     * @param adjustments late events applied to this period; priced marginally on top of the period's own quantity
     */
    static Result rate(PlanView plan, Map<UUID, BigDecimal> quantities, List<UsageSource.AdjustmentView> adjustments) {
        Currency currency = plan.currency();
        List<Line> lines = new ArrayList<>();
        Money subtotal = Money.zero(currency);

        Map<UUID, BigDecimal> adjustmentByMeter = new LinkedHashMap<>();
        for (UsageSource.AdjustmentView a : adjustments) {
            adjustmentByMeter.merge(a.meterId(), a.value(), BigDecimal::add);
        }

        for (ComponentView component : plan.components()) {
            BigDecimal quantity = Decimals.quantity(quantities.getOrDefault(component.meterId(), BigDecimal.ZERO));
            Priced base = price(component, quantity, currency);
            Money baseAmount = base.amount().rounded();
            lines.add(new Line(
                    LineKind.USAGE,
                    component.id(),
                    component.meterId(),
                    component.meterCode() + " (" + component.pricingModel() + ")",
                    quantity,
                    baseAmount,
                    base.breakdown()));
            subtotal = subtotal.plus(baseAmount);

            BigDecimal adjustment = adjustmentByMeter.get(component.meterId());
            if (adjustment != null && adjustment.signum() != 0) {
                BigDecimal combined = Decimals.quantity(quantity.add(adjustment));
                Priced withAdjustment = price(component, combined, currency);
                Money marginal = withAdjustment.amount().minus(base.amount()).rounded();
                Breakdown bd = withAdjustment.breakdown();
                lines.add(new Line(
                        LineKind.ADJUSTMENT,
                        component.id(),
                        component.meterId(),
                        component.meterCode() + " late usage from earlier periods",
                        Decimals.quantity(adjustment),
                        marginal,
                        new Breakdown(
                                bd.pricingModel(),
                                combined,
                                bd.includedUnits(),
                                bd.billableUnits(),
                                bd.packageSize(),
                                bd.packages(),
                                bd.tiers(),
                                quantity,
                                base.amount().rounded().amount())));
                subtotal = subtotal.plus(marginal);
            }
        }

        if (plan.minimumAmount() != null && plan.minimumAmount().signum() > 0) {
            Money minimum = Money.of(plan.minimumAmount(), currency).rounded();
            if (subtotal.compareTo(minimum) < 0) {
                Money topUp = minimum.minus(subtotal);
                lines.add(new Line(
                        LineKind.MINIMUM_COMMITMENT,
                        null,
                        null,
                        "Minimum commitment top-up to " + minimum,
                        BigDecimal.ONE.setScale(Decimals.QUANTITY_SCALE),
                        topUp,
                        new Breakdown(
                                "MINIMUM",
                                BigDecimal.ONE,
                                null,
                                null,
                                null,
                                null,
                                List.of(),
                                null,
                                subtotal.amount())));
                subtotal = minimum;
            }
        }
        return new Result(List.copyOf(lines), subtotal, hash(currency, lines));
    }

    private record Priced(Money amount, Breakdown breakdown) {}

    static Priced price(ComponentView c, BigDecimal quantity, Currency currency) {
        BigDecimal included = c.includedUnits() == null ? BigDecimal.ZERO : c.includedUnits();
        BigDecimal billable = Decimals.max(BigDecimal.ZERO, quantity.subtract(included));
        List<TierView> tiers = c.tiers();
        return switch (c.pricingModel()) {
            case FLAT -> {
                BigDecimal flat =
                        tiers.isEmpty() ? BigDecimal.ZERO : nz(tiers.get(0).flatAmount());
                yield new Priced(
                        Money.of(flat, currency),
                        new Breakdown(
                                "FLAT",
                                quantity,
                                included,
                                billable,
                                null,
                                null,
                                List.of(new TierBreakdown(
                                        0, BigDecimal.ZERO, null, BigDecimal.ZERO, BigDecimal.ZERO, flat, flat)),
                                null,
                                null));
            }
            case PER_UNIT -> {
                TierView t = tiers.get(0);
                BigDecimal amount = billable.multiply(nz(t.unitPrice())).add(nz(t.flatAmount()));
                yield new Priced(
                        Money.of(amount, currency),
                        new Breakdown(
                                "PER_UNIT",
                                quantity,
                                included,
                                billable,
                                null,
                                null,
                                List.of(new TierBreakdown(
                                        0,
                                        BigDecimal.ZERO,
                                        null,
                                        billable,
                                        nz(t.unitPrice()),
                                        nz(t.flatAmount()),
                                        amount)),
                                null,
                                null));
            }
            case GRADUATED -> {
                List<TierBreakdown> parts = new ArrayList<>();
                BigDecimal total = BigDecimal.ZERO;
                BigDecimal lower = BigDecimal.ZERO;
                for (int i = 0; i < tiers.size(); i++) {
                    TierView t = tiers.get(i);
                    BigDecimal upper = t.upTo(); // null = unbounded
                    if (billable.compareTo(lower) <= 0) {
                        break;
                    }
                    BigDecimal top = upper == null ? billable : Decimals.min(upper, billable);
                    BigDecimal units = top.subtract(lower);
                    BigDecimal amount = units.multiply(nz(t.unitPrice())).add(nz(t.flatAmount()));
                    parts.add(new TierBreakdown(i, lower, upper, units, nz(t.unitPrice()), nz(t.flatAmount()), amount));
                    total = total.add(amount);
                    if (upper == null) {
                        break;
                    }
                    lower = upper;
                }
                yield new Priced(
                        Money.of(total, currency),
                        new Breakdown("GRADUATED", quantity, included, billable, null, null, parts, null, null));
            }
            case VOLUME -> {
                if (billable.signum() == 0) {
                    yield new Priced(
                            Money.zero(currency),
                            new Breakdown("VOLUME", quantity, included, billable, null, null, List.of(), null, null));
                }
                int index = tiers.size() - 1;
                for (int i = 0; i < tiers.size(); i++) {
                    BigDecimal upper = tiers.get(i).upTo();
                    if (upper == null || billable.compareTo(upper) <= 0) {
                        index = i;
                        break;
                    }
                }
                TierView t = tiers.get(index);
                BigDecimal amount = billable.multiply(nz(t.unitPrice())).add(nz(t.flatAmount()));
                BigDecimal lower =
                        index == 0 ? BigDecimal.ZERO : tiers.get(index - 1).upTo();
                yield new Priced(
                        Money.of(amount, currency),
                        new Breakdown(
                                "VOLUME",
                                quantity,
                                included,
                                billable,
                                null,
                                null,
                                List.of(new TierBreakdown(
                                        index,
                                        lower,
                                        t.upTo(),
                                        billable,
                                        nz(t.unitPrice()),
                                        nz(t.flatAmount()),
                                        amount)),
                                null,
                                null));
            }
            case PACKAGE -> {
                BigDecimal size = c.packageSize();
                BigDecimal packages =
                        billable.signum() == 0 ? BigDecimal.ZERO : billable.divide(size, 0, RoundingMode.CEILING);
                BigDecimal packagePrice =
                        tiers.isEmpty() ? BigDecimal.ZERO : nz(tiers.get(0).flatAmount());
                BigDecimal amount = packages.multiply(packagePrice);
                yield new Priced(
                        Money.of(amount, currency),
                        new Breakdown(
                                "PACKAGE",
                                quantity,
                                included,
                                billable,
                                size,
                                packages,
                                List.of(new TierBreakdown(
                                        0, BigDecimal.ZERO, null, billable, BigDecimal.ZERO, packagePrice, amount)),
                                null,
                                null));
            }
        };
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /** SHA-256 over a canonical rendering of the lines; stored with the invoice and recomputed by replay. */
    static String hash(Currency currency, List<Line> lines) {
        StringBuilder sb = new StringBuilder(currency.getCurrencyCode()).append('\n');
        for (Line l : lines) {
            sb.append(l.kind())
                    .append('|')
                    .append(l.componentId())
                    .append('|')
                    .append(l.meterId())
                    .append('|')
                    .append(l.quantity().stripTrailingZeros().toPlainString())
                    .append('|')
                    .append(l.amount().rounded().amount().toPlainString())
                    .append('\n');
        }
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256").digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
