package io.tallyard.metering.internal;

import io.tallyard.billing.BillingPeriodView;
import io.tallyard.billing.BillingPeriods;
import io.tallyard.catalog.CatalogQueries;
import io.tallyard.catalog.CustomerView;
import io.tallyard.catalog.MeterView;
import io.tallyard.catalog.SubscriptionView;
import io.tallyard.ingestion.UsageEventMessage;
import io.tallyard.metering.RejectionReason;
import io.tallyard.shared.json.Json;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Processes one consumed batch in one database transaction.
 *
 * <ol>
 *   <li>Collapse duplicates inside the batch (first occurrence wins).</li>
 *   <li>Insert into the immutable log with {@code ON CONFLICT DO NOTHING RETURNING}; only rows that
 *       were really inserted continue. Everything else is a duplicate from an earlier delivery.</li>
 *   <li>Resolve meters, customer, subscription and billing period for each new event. The period
 *       row is locked {@code FOR SHARE} so a concurrent rating cannot settle it underneath us.</li>
 *   <li>Fold values into per-(period, meter, customer, hour) deltas and upsert them in one batch.
 *       Late events (period already RATED/FINALIZED) become adjustments on the open period.</li>
 *   <li>Record rejections with a reason.</li>
 * </ol>
 *
 * <p>Because the whole batch is one transaction and the log insert is idempotent, a crash at any
 * point is safe: either nothing is visible, or everything is and redelivery changes nothing.
 */
@Service
class MeteringService {

    private static final Logger log = LoggerFactory.getLogger(MeteringService.class);

    private final UsageEventStore store;
    private final CatalogQueries catalog;
    private final BillingPeriods periods;
    private final Json json;
    private final Clock clock;

    MeteringService(UsageEventStore store, CatalogQueries catalog, BillingPeriods periods, Json json, Clock clock) {
        this.store = store;
        this.catalog = catalog;
        this.periods = periods;
        this.json = json;
        this.clock = clock;
    }

    record BatchResult(int stored, int duplicates, int late, Map<RejectionReason, Integer> rejected) {
        static BatchResult empty() {
            return new BatchResult(0, 0, 0, Map.of());
        }
    }

    record Poison(String key, String rawValue, UUID tenantId, String detail) {}

    @Transactional
    public BatchResult process(List<UsageEventMessage> messages, List<Poison> poison) {
        Batch batch = new Batch();
        for (Poison p : poison) {
            if (p.tenantId() != null) {
                // The raw value is by definition not valid JSON; wrap it so the jsonb column accepts it.
                String payload = json.write(
                        Map.of("raw", p.rawValue() == null ? "" : p.rawValue(), "key", p.key() == null ? "" : p.key()));
                batch.reject(p.tenantId(), null, null, null, null, RejectionReason.SCHEMA_INVALID, p.detail(), payload);
            }
        }
        if (messages.isEmpty()) {
            batch.flush();
            return batch.result();
        }

        // 1. In-batch deduplication: first occurrence of (tenant, eventId) wins.
        Map<String, UsageEventMessage> unique = new LinkedHashMap<>();
        for (UsageEventMessage m : messages) {
            if (unique.putIfAbsent(UsageEventStore.key(m.tenantId(), m.eventId()), m) != null) {
                batch.duplicates++;
            }
        }

        // 2. Idempotent log insert.
        Map<String, UUID> inserted = store.insertNew(unique.values());
        batch.duplicates += unique.size() - inserted.size();
        batch.stored = inserted.size();

        // 3./4. Resolve and fold.
        for (Map.Entry<String, UsageEventMessage> e : unique.entrySet()) {
            UUID usageEventId = inserted.get(e.getKey());
            if (usageEventId != null) {
                count(e.getValue(), usageEventId, batch);
            }
        }

        // 5. Persist deltas, adjustments and rejections.
        batch.flush();
        return batch.result();
    }

    private void count(UsageEventMessage m, UUID usageEventId, Batch batch) {
        List<MeterView> meters = catalog.metersForEventType(m.tenantId(), m.eventType());
        if (meters.isEmpty()) {
            batch.reject(
                    m, RejectionReason.NO_MATCHING_METER, "no meter listens to event type '" + m.eventType() + "'");
            return;
        }
        Optional<CustomerView> customer = catalog.customerByExternalId(m.tenantId(), m.subject());
        if (customer.isEmpty()) {
            batch.reject(m, RejectionReason.UNKNOWN_SUBJECT, "subject '" + m.subject() + "' is not a customer");
            return;
        }
        Optional<SubscriptionView> subscription =
                batch.subscriptionAt(customer.get().id(), m.occurredAt());
        if (subscription.isEmpty()) {
            batch.reject(m, RejectionReason.NO_ACTIVE_SUBSCRIPTION, "no active subscription at " + m.occurredAt());
            return;
        }
        Optional<BillingPeriodView> period = batch.periodAt(subscription.get().id(), m.occurredAt());

        for (MeterView meter : meters) {
            Instant oldestAccepted = m.ingestedAt().minus(meter.acceptanceWindow());
            if (m.occurredAt().isBefore(oldestAccepted)) {
                batch.reject(
                        m,
                        RejectionReason.OUTSIDE_ACCEPTANCE_WINDOW,
                        "meter '" + meter.code() + "' accepts events at most " + meter.acceptanceWindow() + " old");
                continue;
            }
            Optional<ValueExtractor.Value> value = ValueExtractor.extract(meter, m.properties());
            if (value.isEmpty()) {
                batch.reject(
                        m,
                        RejectionReason.INVALID_VALUE,
                        "meter '" + meter.code() + "' needs a plain decimal property '" + meter.valueProperty() + "'");
                continue;
            }
            if (period.isEmpty()) {
                batch.reject(m, RejectionReason.NO_PERIOD, "no billing period covers " + m.occurredAt());
                continue;
            }
            BillingPeriodView p = period.get();
            if (p.state().acceptsUsage()) {
                batch.accumulate(p, meter, customer.get(), m, value.get());
                batch.attributed.put(usageEventId, p.id());
            } else {
                Optional<BillingPeriodView> open =
                        batch.openPeriod(subscription.get().id());
                if (open.isEmpty()) {
                    batch.reject(
                            m,
                            RejectionReason.NO_OPEN_PERIOD,
                            "meter '" + meter.code() + "': period " + p.id() + " is " + p.state()
                                    + " and no open period exists");
                    continue;
                }
                if (meter.aggregation().isAdditive()) {
                    batch.adjust(open.get(), p, subscription.get(), meter, usageEventId, m, value.get());
                } else {
                    // MIN/MAX/LAST/UNIQUE_COUNT cannot be carried forward additively; kept in the log for audit.
                    batch.reject(
                            m,
                            RejectionReason.LATE_NOT_ADJUSTABLE,
                            "meter '" + meter.code() + "' (" + meter.aggregation()
                                    + ") cannot absorb late events; period " + p.id() + " is " + p.state());
                }
            }
        }
    }

    /** Per-batch working state: lookup caches scoped to the transaction and pending writes. */
    private final class Batch {
        int stored;
        int duplicates;
        int late;
        final Map<RejectionReason, Integer> rejected = new EnumMap<>(RejectionReason.class);
        final Map<UUID, List<SubscriptionView>> subscriptionsByCustomer = new HashMap<>();
        final Map<UUID, List<BillingPeriodView>> periodsBySubscription = new HashMap<>();
        final Map<UUID, Optional<BillingPeriodView>> openBySubscription = new HashMap<>();
        final Map<String, UsageEventStore.BucketDelta> deltas = new LinkedHashMap<>();
        final Map<UUID, UUID> attributed = new LinkedHashMap<>();
        final List<UsageEventStore.Adjustment> adjustments = new ArrayList<>();
        final List<UsageEventStore.Rejection> rejections = new ArrayList<>();

        Optional<SubscriptionView> subscriptionAt(UUID customerId, Instant at) {
            List<SubscriptionView> known = subscriptionsByCustomer.computeIfAbsent(customerId, k -> new ArrayList<>(1));
            for (SubscriptionView s : known) {
                if (s.coversInstant(at)) {
                    return Optional.of(s);
                }
            }
            Optional<SubscriptionView> found = catalog.activeSubscriptionAt(customerId, at);
            found.ifPresent(known::add);
            return found;
        }

        Optional<BillingPeriodView> periodAt(UUID subscriptionId, Instant at) {
            List<BillingPeriodView> known =
                    periodsBySubscription.computeIfAbsent(subscriptionId, k -> new ArrayList<>(2));
            for (BillingPeriodView p : known) {
                if (p.covers(at)) {
                    return Optional.of(p);
                }
            }
            Optional<BillingPeriodView> found = periods.lockPeriodCovering(subscriptionId, at);
            found.ifPresent(known::add);
            return found;
        }

        Optional<BillingPeriodView> openPeriod(UUID subscriptionId) {
            return openBySubscription.computeIfAbsent(subscriptionId, id -> {
                Optional<BillingPeriodView> open = periods.lockOpenPeriod(id);
                open.ifPresent(p -> periodsBySubscription
                        .computeIfAbsent(id, k -> new ArrayList<>(2))
                        .add(p));
                return open;
            });
        }

        void accumulate(
                BillingPeriodView period,
                MeterView meter,
                CustomerView customer,
                UsageEventMessage m,
                ValueExtractor.Value v) {
            Instant hour = m.occurredAt().truncatedTo(ChronoUnit.HOURS);
            String key = period.id() + "|" + meter.id() + "|" + customer.id() + "|" + hour;
            BigDecimal n = v.numeric() == null ? null : v.numeric();
            deltas.merge(
                    key,
                    new UsageEventStore.BucketDelta(
                            m.tenantId(),
                            period.id(),
                            meter.id(),
                            customer.id(),
                            hour,
                            n == null ? BigDecimal.ZERO : n,
                            1,
                            n,
                            n,
                            n,
                            n == null ? null : m.occurredAt()),
                    (a, b) -> new UsageEventStore.BucketDelta(
                            a.tenantId(),
                            a.periodId(),
                            a.meterId(),
                            a.customerId(),
                            a.bucketStart(),
                            a.sum().add(b.sum()),
                            a.count() + b.count(),
                            minOf(a.min(), b.min()),
                            maxOf(a.max(), b.max()),
                            b.lastOccurredAt() != null
                                            && (a.lastOccurredAt() == null
                                                    || !b.lastOccurredAt().isBefore(a.lastOccurredAt()))
                                    ? b.last()
                                    : a.last(),
                            laterOf(a.lastOccurredAt(), b.lastOccurredAt())));
        }

        void adjust(
                BillingPeriodView open,
                BillingPeriodView original,
                SubscriptionView subscription,
                MeterView meter,
                UUID usageEventId,
                UsageEventMessage m,
                ValueExtractor.Value v) {
            late++;
            adjustments.add(new UsageEventStore.Adjustment(
                    m.tenantId(),
                    subscription.id(),
                    open.id(),
                    original.id(),
                    meter.id(),
                    usageEventId,
                    v.numeric(),
                    m.occurredAt()));
        }

        void reject(UsageEventMessage m, RejectionReason reason, String detail) {
            reject(m.tenantId(), m.eventId(), m.eventType(), m.subject(), m.occurredAt(), reason, detail, null);
        }

        void reject(
                UUID tenantId,
                String eventId,
                String eventType,
                String subject,
                Instant occurredAt,
                RejectionReason reason,
                String detail,
                String payload) {
            rejected.merge(reason, 1, Integer::sum);
            rejections.add(new UsageEventStore.Rejection(
                    tenantId, eventId, eventType, subject, occurredAt, reason, detail, payload));
        }

        void flush() {
            List<UsageEventStore.BucketDelta> sorted = new ArrayList<>(deltas.values());
            // Deterministic order avoids lock-order deadlocks between concurrent consumer threads.
            sorted.sort((a, b) -> {
                int c = a.periodId().compareTo(b.periodId());
                if (c != 0) return c;
                c = a.meterId().compareTo(b.meterId());
                if (c != 0) return c;
                c = a.customerId().compareTo(b.customerId());
                return c != 0 ? c : a.bucketStart().compareTo(b.bucketStart());
            });
            store.upsertBuckets(sorted);
            store.attributeToPeriod(attributed);
            store.insertAdjustments(adjustments);
            store.insertRejections(rejections);
            if (!rejections.isEmpty() || late > 0) {
                log.debug("batch: stored={} duplicates={} late={} rejected={}", stored, duplicates, late, rejected);
            }
        }

        BatchResult result() {
            return new BatchResult(stored, duplicates, late, Map.copyOf(rejected));
        }
    }

    private static BigDecimal minOf(BigDecimal a, BigDecimal b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.compareTo(b) <= 0 ? a : b;
    }

    private static BigDecimal maxOf(BigDecimal a, BigDecimal b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.compareTo(b) >= 0 ? a : b;
    }

    private static Instant laterOf(Instant a, Instant b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isAfter(b) ? a : b;
    }
}
