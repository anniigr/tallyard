package io.tallyard.billing.internal;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.tallyard.billing.BillingPeriodView;
import io.tallyard.billing.BillingPeriods;
import io.tallyard.billing.BillingQueries;
import io.tallyard.billing.InvoiceView;
import io.tallyard.billing.PeriodState;
import io.tallyard.billing.ReplayReport;
import io.tallyard.billing.UsageSource;
import io.tallyard.catalog.CatalogQueries;
import io.tallyard.catalog.CustomerView;
import io.tallyard.catalog.MeterView;
import io.tallyard.catalog.PlanView;
import io.tallyard.catalog.SubscriptionActivated;
import io.tallyard.catalog.SubscriptionView;
import io.tallyard.credits.CreditLedger;
import io.tallyard.outbox.Outbox;
import io.tallyard.shared.config.TallyardProperties;
import io.tallyard.shared.error.ConflictException;
import io.tallyard.shared.error.NotFoundException;
import io.tallyard.shared.id.Ids;
import io.tallyard.shared.json.Json;
import io.tallyard.shared.money.Money;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Period lifecycle, rating and replay. Every transition is one transaction that takes an exclusive
 * row lock on the period (waiting for in-flight metering writes that hold shared locks) and relies on
 * {@code @Version} as a second line of defence against concurrent schedulers.
 */
@Service
class BillingService implements BillingPeriods, BillingQueries {

    private static final Logger log = LoggerFactory.getLogger(BillingService.class);

    private final BillingPeriodRepository periods;
    private final InvoiceDraftRepository invoices;
    private final CatalogQueries catalog;
    private final UsageSource usage;
    private final CreditLedger credits;
    private final Outbox outbox;
    private final Json json;
    private final Clock clock;
    private final TallyardProperties props;
    private final Timer ratingTimer;
    private final Timer replayTimer;

    BillingService(
            BillingPeriodRepository periods,
            InvoiceDraftRepository invoices,
            CatalogQueries catalog,
            UsageSource usage,
            CreditLedger credits,
            Outbox outbox,
            Json json,
            Clock clock,
            TallyardProperties props,
            MeterRegistry registry) {
        this.periods = periods;
        this.invoices = invoices;
        this.catalog = catalog;
        this.usage = usage;
        this.credits = credits;
        this.outbox = outbox;
        this.json = json;
        this.clock = clock;
        this.props = props;
        this.ratingTimer = Timer.builder("tallyard.billing.rating")
                .description("Time to rate one period")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
        this.replayTimer = Timer.builder("tallyard.billing.replay")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }

    // ------------------------------------------------------------------ lifecycle

    /** Runs inside the catalog transaction that created the subscription: the first period is atomic with it. */
    @EventListener
    @Transactional
    public void onSubscriptionActivated(SubscriptionActivated event) {
        SubscriptionView s = event.subscription();
        openPeriod(s, event.plan(), s.startsAt());
    }

    private BillingPeriod openPeriod(SubscriptionView s, PlanView plan, Instant startsAt) {
        Instant endsAt = plan.interval().endOf(startsAt);
        if (s.endsAt() != null && !s.endsAt().isAfter(startsAt)) {
            return null;
        }
        BillingPeriod period = new BillingPeriod(
                Ids.newId(clock),
                s.tenantId(),
                s.id(),
                s.customerId(),
                startsAt,
                endsAt,
                endsAt.plusSeconds(plan.graceSeconds()),
                clock.instant());
        periods.save(period);
        outbox.append(s.tenantId(), "BillingPeriod", period.getId(), "period.opened", periodPayload(period, null));
        return period;
    }

    @Transactional
    public BillingPeriodView close(UUID tenantId, UUID periodId) {
        BillingPeriod period = lockOwned(tenantId, periodId);
        closeLocked(period);
        return period.toView();
    }

    private void closeLocked(BillingPeriod period) {
        period.startClosing();
        // Flush the state change first: Hibernate orders inserts before updates, and the partial
        // unique index billing_period_one_open would otherwise reject the successor period.
        periods.flush();
        SubscriptionView s = subscription(period.getSubscriptionId());
        PlanView plan = plan(s.planId());
        openPeriod(s, plan, period.getEndsAt());
        outbox.append(
                period.getTenantId(), "BillingPeriod", period.getId(), "period.closing", periodPayload(period, null));
    }

    /** Forces OPEN → CLOSING → RATED in one transaction; used by operators and demos. */
    @Transactional
    public InvoiceView rateNow(UUID tenantId, UUID periodId) {
        BillingPeriod period = lockOwned(tenantId, periodId);
        if (period.getState() == PeriodState.OPEN) {
            closeLocked(period);
        }
        return rateLocked(period).toView();
    }

    @Transactional
    public BillingPeriodView finalizePeriod(UUID tenantId, UUID periodId) {
        BillingPeriod period = lockOwned(tenantId, periodId);
        finalizeLocked(period);
        return period.toView();
    }

    private void finalizeLocked(BillingPeriod period) {
        period.finalizePeriod(clock.instant());
        outbox.append(
                period.getTenantId(), "BillingPeriod", period.getId(), "period.finalized", periodPayload(period, null));
    }

    private InvoiceDraft rateLocked(BillingPeriod period) {
        Timer.Sample sample = Timer.start();
        Instant now = clock.instant();
        if (period.getState() != PeriodState.CLOSING) {
            throw new ConflictException(
                    "period.illegal_transition", "Only a CLOSING period can be rated; period is " + period.getState());
        }
        RatingInputs in = inputs(period);
        Map<UUID, BigDecimal> quantities = usage.quantitiesFromBuckets(in.window(), in.meters(), now);
        List<UsageSource.AdjustmentView> adjustments = usage.adjustments(period.getId());
        RatingEngine.Result rated = RatingEngine.rate(in.plan(), quantities, adjustments);

        UUID invoiceId = Ids.newId(clock);
        Money subtotal = rated.subtotal();
        InvoiceDraft invoice = new InvoiceDraft(
                invoiceId,
                period.getTenantId(),
                period.getId(),
                period.getCustomerId(),
                in.plan().currency().getCurrencyCode(),
                subtotal.amount(),
                rated.hash(),
                now);
        int position = 0;
        for (RatingEngine.Line line : rated.lines()) {
            invoice.addLine(new InvoiceLine(
                    Ids.newId(clock),
                    position++,
                    line.kind().name(),
                    line.componentId(),
                    line.meterId(),
                    line.description(),
                    line.quantity(),
                    line.amount().amount(),
                    json.write(line.breakdown())));
        }
        // The invoice row must exist before the credit ledger references it (FK credit_ledger_entry.invoice_id).
        // save() on an entity with an assigned id merges and returns the managed copy; mutate that one.
        invoice = invoices.saveAndFlush(invoice);
        CreditLedger.Application applied =
                credits.apply(period.getTenantId(), period.getCustomerId(), subtotal, invoiceId, now);
        invoice.applyCredits(applied.applied().amount());
        invoices.flush();
        Money total = subtotal.minus(applied.applied());
        period.markRated(now);

        Map<String, Object> payload = periodPayload(period, invoice);
        outbox.append(period.getTenantId(), "BillingPeriod", period.getId(), "period.rated", payload);
        outbox.append(period.getTenantId(), "Invoice", invoice.getId(), "invoice.drafted", payload);
        if (applied.applied().isPositive()
                && credits.balance(period.getCustomerId(), in.plan().currency(), now)
                        .isZero()) {
            outbox.append(
                    period.getTenantId(),
                    "Customer",
                    period.getCustomerId(),
                    "credit.depleted",
                    Map.of(
                            "customerExternalId",
                            in.customer().externalId(),
                            "currency",
                            in.plan().currency().getCurrencyCode()));
        }
        sample.stop(ratingTimer);
        log.info(
                "Rated period {} for {}: subtotal={} credits={} total={} hash={}",
                period.getId(),
                in.customer().externalId(),
                subtotal,
                applied.applied(),
                total,
                rated.hash().substring(0, 12));
        return invoice;
    }

    // ------------------------------------------------------------------ replay

    @Transactional(readOnly = true)
    public ReplayReport replay(UUID tenantId, UUID periodId) {
        long started = System.nanoTime();
        BillingPeriod period = owned(tenantId, periodId);
        if (!PeriodState.settled().contains(period.getState())) {
            throw new ConflictException("period.not_rated", "Only RATED or FINALIZED periods can be replayed");
        }
        InvoiceDraft stored = invoices.findByPeriodIdWithLines(periodId)
                .orElseThrow(() -> new NotFoundException("Invoice for period", periodId));
        RatingInputs in = inputs(period);
        Instant asOf = period.getRatedAt();
        Map<UUID, BigDecimal> fromLog = usage.quantitiesFromEventLog(in.window(), in.meters(), asOf);
        RatingEngine.Result replayed = RatingEngine.rate(in.plan(), fromLog, usage.adjustments(periodId));

        List<String> differences = new ArrayList<>();
        List<InvoiceLine> storedLines = stored.getLines();
        if (storedLines.size() != replayed.lines().size()) {
            differences.add("line count: stored=" + storedLines.size() + " replayed="
                    + replayed.lines().size());
        }
        for (int i = 0; i < Math.min(storedLines.size(), replayed.lines().size()); i++) {
            InvoiceLine s = storedLines.get(i);
            RatingEngine.Line r = replayed.lines().get(i);
            if (!s.getKind().equals(r.kind().name()) || !java.util.Objects.equals(s.getMeterId(), r.meterId())) {
                differences.add("line " + i + ": kind/meter differ");
            }
            if (s.getQuantity().compareTo(r.quantity()) != 0) {
                differences.add("line " + i + " (" + r.description() + "): quantity stored="
                        + s.getQuantity().stripTrailingZeros().toPlainString() + " replayed="
                        + r.quantity().stripTrailingZeros().toPlainString());
            }
            if (s.getAmount().compareTo(r.amount().amount()) != 0) {
                differences.add("line " + i + " (" + r.description() + "): amount stored="
                        + s.getAmount().toPlainString() + " replayed="
                        + r.amount().amount().toPlainString());
            }
        }
        boolean matches =
                differences.isEmpty() && stored.getReproducibilityHash().equals(replayed.hash());
        long millis = Duration.ofNanos(System.nanoTime() - started).toMillis();
        replayTimer.record(Duration.ofMillis(millis));
        return new ReplayReport(
                periodId, matches, stored.getReproducibilityHash(), replayed.hash(), differences, millis);
    }

    // ------------------------------------------------------------------ scheduler entry points

    /**
     * Ids of periods due for a step, claimed without locks. Each period is then processed in its own
     * transaction by {@link #closeOne}, {@link #rateOne} or {@link #finalizeOne}, so one failing period
     * (bad plan, constraint violation) cannot stall the others; the per-period {@code FOR UPDATE} and the
     * state re-check make the work idempotent across concurrent workers.
     */
    @Transactional(readOnly = true)
    public List<UUID> dueForClosing(int limit) {
        return periods.findDueForClosing(clock.instant(), org.springframework.data.domain.Limit.of(limit));
    }

    @Transactional(readOnly = true)
    public List<UUID> dueForRating(int limit) {
        return periods.findDueForRating(clock.instant(), org.springframework.data.domain.Limit.of(limit));
    }

    @Transactional(readOnly = true)
    public List<UUID> dueForFinalizing(int limit) {
        Instant before = clock.instant().minus(props.billing().autoFinalizeAfter());
        return periods.findDueForFinalizing(before, org.springframework.data.domain.Limit.of(limit));
    }

    /** @return true if the step was performed; false if another worker already did it */
    @Transactional
    public boolean closeOne(UUID periodId) {
        BillingPeriod period = periods.lockForUpdate(periodId).orElse(null);
        if (period == null || !period.isDueForClosing(clock.instant())) {
            return false;
        }
        closeLocked(period);
        return true;
    }

    @Transactional
    public boolean rateOne(UUID periodId) {
        BillingPeriod period = periods.lockForUpdate(periodId).orElse(null);
        if (period == null || !period.isDueForRating(clock.instant())) {
            return false;
        }
        rateLocked(period);
        return true;
    }

    @Transactional
    public boolean finalizeOne(UUID periodId) {
        BillingPeriod period = periods.lockForUpdate(periodId).orElse(null);
        if (period == null || period.getState() != PeriodState.RATED) {
            return false;
        }
        finalizeLocked(period);
        return true;
    }

    // ------------------------------------------------------------------ queries

    /** MANDATORY: the shared lock only makes sense inside the caller's (metering) transaction. */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<BillingPeriodView> lockPeriodCovering(UUID subscriptionId, Instant at) {
        return periods.lockShareCovering(subscriptionId, at).map(BillingPeriod::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BillingPeriodView> openPeriod(UUID subscriptionId) {
        return periods.findOpen(subscriptionId).map(BillingPeriod::toView);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<BillingPeriodView> lockOpenPeriod(UUID subscriptionId) {
        return periods.lockShareOpen(subscriptionId).map(BillingPeriod::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BillingPeriodView> period(UUID periodId) {
        return periods.findById(periodId).map(BillingPeriod::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BillingPeriodView> periodsForTenant(UUID tenantId) {
        return periods.findByTenantIdOrderByStartsAtDesc(tenantId).stream()
                .map(BillingPeriod::toView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BillingPeriodView> periodsForCustomer(UUID customerId) {
        return periods.findByCustomerIdOrderByStartsAtDesc(customerId).stream()
                .map(BillingPeriod::toView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InvoiceView> invoiceForPeriod(UUID periodId) {
        return invoices.findByPeriodIdWithLines(periodId).map(InvoiceDraft::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InvoiceView> invoicesForTenant(UUID tenantId, int limit) {
        return invoices.findByTenantWithLines(tenantId, org.springframework.data.domain.Limit.of(limit)).stream()
                .map(InvoiceDraft::toView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Money liveEstimate(UUID periodId) {
        BillingPeriod period =
                periods.findById(periodId).orElseThrow(() -> new NotFoundException("Billing period", periodId));
        RatingInputs in = inputs(period);
        Map<UUID, BigDecimal> quantities = usage.quantitiesFromBuckets(in.window(), in.meters(), clock.instant());
        return RatingEngine.rate(in.plan(), quantities, usage.adjustments(periodId))
                .subtotal();
    }

    @Transactional(readOnly = true)
    BillingPeriodView owned(UUID tenantId, UUID periodId, boolean unused) {
        return owned(tenantId, periodId).toView();
    }

    // ------------------------------------------------------------------ helpers

    private record RatingInputs(
            SubscriptionView subscription,
            CustomerView customer,
            PlanView plan,
            List<MeterView> meters,
            UsageSource.Window window) {}

    private RatingInputs inputs(BillingPeriod period) {
        SubscriptionView s = subscription(period.getSubscriptionId());
        CustomerView customer =
                catalog.customer(s.customerId()).orElseThrow(() -> new NotFoundException("Customer", s.customerId()));
        PlanView plan = plan(s.planId());
        List<MeterView> meters = plan.components().stream()
                .map(c -> catalog.meter(c.meterId()).orElseThrow(() -> new NotFoundException("Meter", c.meterId())))
                .toList();
        UsageSource.Window window = new UsageSource.Window(
                period.getTenantId(),
                period.getId(),
                customer.id(),
                customer.externalId(),
                period.getStartsAt(),
                period.getEndsAt());
        return new RatingInputs(s, customer, plan, meters, window);
    }

    private SubscriptionView subscription(UUID id) {
        return catalog.subscription(id).orElseThrow(() -> new NotFoundException("Subscription", id));
    }

    private PlanView plan(UUID id) {
        return catalog.plan(id).orElseThrow(() -> new NotFoundException("Plan", id));
    }

    private BillingPeriod lockOwned(UUID tenantId, UUID periodId) {
        BillingPeriod period =
                periods.lockForUpdate(periodId).orElseThrow(() -> new NotFoundException("Billing period", periodId));
        if (!period.getTenantId().equals(tenantId)) {
            throw new NotFoundException("Billing period", periodId);
        }
        return period;
    }

    private BillingPeriod owned(UUID tenantId, UUID periodId) {
        return periods.findByIdAndTenantId(periodId, tenantId)
                .orElseThrow(() -> new NotFoundException("Billing period", periodId));
    }

    private Map<String, Object> periodPayload(BillingPeriod period, InvoiceDraft invoice) {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("periodId", period.getId());
        payload.put("subscriptionId", period.getSubscriptionId());
        payload.put("customerId", period.getCustomerId());
        payload.put("startsAt", period.getStartsAt());
        payload.put("endsAt", period.getEndsAt());
        payload.put("state", period.getState());
        if (invoice != null) {
            payload.put("invoiceId", invoice.getId());
            payload.put("currency", invoice.getCurrency());
            payload.put("subtotal", invoice.getSubtotal());
            payload.put("creditsApplied", invoice.getCreditsApplied());
            payload.put("total", invoice.getTotal());
            payload.put("reproducibilityHash", invoice.getReproducibilityHash());
        }
        return payload;
    }
}
