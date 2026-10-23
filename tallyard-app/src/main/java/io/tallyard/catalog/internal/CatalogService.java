package io.tallyard.catalog.internal;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.tallyard.catalog.CatalogCommands;
import io.tallyard.catalog.CatalogQueries;
import io.tallyard.catalog.CustomerView;
import io.tallyard.catalog.MeterView;
import io.tallyard.catalog.PlanView;
import io.tallyard.catalog.SubscriptionActivated;
import io.tallyard.catalog.SubscriptionView;
import io.tallyard.shared.error.ConflictException;
import io.tallyard.shared.error.NotFoundException;
import io.tallyard.shared.error.ValidationException;
import io.tallyard.shared.id.Ids;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Catalog commands and queries. Per-event lookups ({@link #metersForEventType},
 * {@link #customerByExternalId}, {@link #activeSubscriptionAt}, {@link #plan}) sit on the metering
 * hot path, so they are cached for a short time; writes invalidate the local cache. The TTL is the
 * worst-case staleness between an API node and a worker node.
 */
@Service
class CatalogService implements CatalogQueries, CatalogCommands {

    private static final Duration CACHE_TTL = Duration.ofSeconds(15);

    private final MeterRepository meters;
    private final CustomerRepository customers;
    private final PlanRepository plans;
    private final SubscriptionRepository subscriptions;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    private final Cache<String, List<MeterView>> metersByType = cache();
    private final Cache<String, Optional<CustomerView>> customersByExternalId = cache();
    private final Cache<UUID, Optional<PlanView>> plansById = cache();
    private final Cache<UUID, Optional<MeterView>> metersById = cache();

    CatalogService(
            MeterRepository meters,
            CustomerRepository customers,
            PlanRepository plans,
            SubscriptionRepository subscriptions,
            ApplicationEventPublisher events,
            Clock clock) {
        this.meters = meters;
        this.customers = customers;
        this.plans = plans;
        this.subscriptions = subscriptions;
        this.events = events;
        this.clock = clock;
    }

    private static <K, V> Cache<K, V> cache() {
        return Caffeine.newBuilder()
                .maximumSize(50_000)
                .expireAfterWrite(CACHE_TTL)
                .build();
    }

    // ------------------------------------------------------------------ commands

    @Override
    @Transactional
    public MeterView createMeter(UUID tenantId, NewMeter cmd) {
        if (cmd.aggregation().requiresValue()
                && (cmd.valueProperty() == null || cmd.valueProperty().isBlank())) {
            throw new ValidationException(
                    "meter.value_property_required", "valueProperty is required for " + cmd.aggregation());
        }
        Meter meter = new Meter(
                Ids.newId(clock),
                tenantId,
                cmd.code(),
                cmd.name(),
                cmd.eventType(),
                cmd.aggregation(),
                cmd.aggregation().requiresValue() ? cmd.valueProperty() : null,
                cmd.unit(),
                cmd.acceptanceWindow() == null ? Duration.ofDays(35) : cmd.acceptanceWindow(),
                clock.instant());
        try {
            meters.saveAndFlush(meter);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException(
                    "meter.duplicate_code", "A meter with code '" + cmd.code() + "' already exists");
        }
        metersByType.invalidate(key(tenantId, cmd.eventType()));
        return meter.toView();
    }

    @Override
    @Transactional
    public CustomerView createCustomer(UUID tenantId, String externalId, String name) {
        Customer customer = new Customer(Ids.newId(clock), tenantId, externalId, name, clock.instant());
        try {
            customers.saveAndFlush(customer);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException(
                    "customer.duplicate_external_id", "Customer '" + externalId + "' already exists");
        }
        customersByExternalId.invalidate(key(tenantId, externalId));
        return customer.toView();
    }

    @Override
    @Transactional
    public PlanView createPlan(UUID tenantId, NewPlan cmd) {
        if (cmd.components() == null || cmd.components().isEmpty()) {
            throw new ValidationException("plan.no_components", "A plan needs at least one component");
        }
        Plan plan = new Plan(
                Ids.newId(clock),
                tenantId,
                cmd.code(),
                cmd.name(),
                cmd.currency(),
                cmd.interval(),
                cmd.graceSeconds(),
                cmd.minimumAmount() == null ? BigDecimal.ZERO : cmd.minimumAmount(),
                clock.instant());
        Set<String> seen = new HashSet<>();
        int position = 0;
        for (NewComponent c : cmd.components()) {
            if (!seen.add(c.meterCode())) {
                throw new ValidationException("plan.duplicate_meter", "Meter '" + c.meterCode() + "' appears twice");
            }
            Meter meter = meters.findByTenantIdAndCode(tenantId, c.meterCode())
                    .orElseThrow(() -> new NotFoundException("Meter", c.meterCode()));
            PlanComponent component = new PlanComponent(
                    Ids.newId(clock),
                    meter.getId(),
                    position++,
                    c.pricingModel(),
                    c.includedUnits() == null ? BigDecimal.ZERO : c.includedUnits(),
                    c.packageSize(),
                    c.hardLimit());
            validateComponent(c);
            int tierPos = 0;
            for (NewTier t : c.tiers() == null ? List.<NewTier>of() : c.tiers()) {
                component.addTier(new PriceTier(
                        Ids.newId(clock),
                        tierPos++,
                        t.upTo(),
                        t.unitPrice() == null ? BigDecimal.ZERO : t.unitPrice(),
                        t.flatAmount() == null ? BigDecimal.ZERO : t.flatAmount()));
            }
            plan.addComponent(component);
        }
        try {
            plans.saveAndFlush(plan);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("plan.duplicate_code", "A plan with code '" + cmd.code() + "' already exists");
        }
        return toView(plan);
    }

    private static void validateComponent(NewComponent c) {
        List<NewTier> tiers = c.tiers() == null ? List.of() : c.tiers();
        switch (c.pricingModel()) {
            case FLAT -> {
                if (tiers.size() != 1) {
                    throw new ValidationException(
                            "plan.flat_tier", "FLAT requires exactly one tier carrying flatAmount");
                }
            }
            case PER_UNIT -> {
                if (tiers.size() != 1 || tiers.get(0).upTo() != null) {
                    throw new ValidationException("plan.per_unit_tier", "PER_UNIT requires exactly one unbounded tier");
                }
            }
            case GRADUATED, VOLUME -> {
                if (tiers.isEmpty()) {
                    throw new ValidationException("plan.tiers_required", c.pricingModel() + " requires tiers");
                }
                BigDecimal previous = BigDecimal.ZERO;
                for (int i = 0; i < tiers.size(); i++) {
                    BigDecimal upTo = tiers.get(i).upTo();
                    boolean last = i == tiers.size() - 1;
                    if (!last && (upTo == null || upTo.compareTo(previous) <= 0)) {
                        throw new ValidationException(
                                "plan.tier_bounds",
                                "Tier bounds must be strictly increasing; only the last tier may be unbounded");
                    }
                    if (last && upTo != null) {
                        throw new ValidationException(
                                "plan.tier_bounds", "The last tier must be unbounded (upTo = null)");
                    }
                    if (upTo != null) {
                        previous = upTo;
                    }
                }
            }
            case PACKAGE -> {
                if (c.packageSize() == null || c.packageSize().signum() <= 0 || tiers.size() != 1) {
                    throw new ValidationException(
                            "plan.package",
                            "PACKAGE requires packageSize > 0 and one tier whose flatAmount is the package price");
                }
            }
        }
    }

    @Override
    @Transactional
    public SubscriptionView subscribe(UUID tenantId, String customerExternalId, String planCode, Instant startsAt) {
        Customer customer = customers
                .findByTenantIdAndExternalId(tenantId, customerExternalId)
                .orElseThrow(() -> new NotFoundException("Customer", customerExternalId));
        Plan plan = plans.findByTenantIdAndCode(tenantId, planCode)
                .flatMap(p -> plans.findWithComponents(p.getId()))
                .orElseThrow(() -> new NotFoundException("Plan", planCode));
        if (subscriptions.existsByCustomerIdAndEndsAtIsNull(customer.getId())) {
            throw new ConflictException("subscription.already_active", "Customer already has an open subscription");
        }
        Instant start = startsAt == null ? clock.instant() : startsAt;
        Subscription subscription =
                new Subscription(Ids.newId(clock), tenantId, customer.getId(), plan.getId(), start, clock.instant());
        subscriptions.saveAndFlush(subscription);
        if (!plan.isLocked()) {
            plan.lock();
            plansById.invalidate(plan.getId());
        }
        SubscriptionView view = subscription.toView();
        events.publishEvent(new SubscriptionActivated(view, toView(plan)));
        return view;
    }

    @Override
    @Transactional
    public SubscriptionView endSubscription(UUID tenantId, UUID subscriptionId, Instant at) {
        Subscription subscription = subscriptions
                .findByIdAndTenantId(subscriptionId, tenantId)
                .orElseThrow(() -> new NotFoundException("Subscription", subscriptionId));
        subscription.end(at == null ? clock.instant() : at);
        return subscription.toView();
    }

    // ------------------------------------------------------------------ queries

    @Override
    @Transactional(readOnly = true)
    public List<MeterView> metersForEventType(UUID tenantId, String eventType) {
        return metersByType.get(
                key(tenantId, eventType),
                k -> meters.findByTenantIdAndEventType(tenantId, eventType).stream()
                        .map(Meter::toView)
                        .toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<MeterView> meters(UUID tenantId) {
        return meters.findByTenantIdOrderByCode(tenantId).stream()
                .map(Meter::toView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MeterView> meter(UUID meterId) {
        return metersById.get(meterId, id -> meters.findById(id).map(Meter::toView));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MeterView> meterByCode(UUID tenantId, String code) {
        return meters.findByTenantIdAndCode(tenantId, code).map(Meter::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CustomerView> customerByExternalId(UUID tenantId, String externalId) {
        return customersByExternalId.get(
                key(tenantId, externalId),
                k -> customers.findByTenantIdAndExternalId(tenantId, externalId).map(Customer::toView));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CustomerView> customer(UUID customerId) {
        return customers.findById(customerId).map(Customer::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerView> customers(UUID tenantId) {
        return customers.findByTenantIdOrderByExternalId(tenantId).stream()
                .map(Customer::toView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SubscriptionView> activeSubscriptionAt(UUID customerId, Instant at) {
        return subscriptions.findActiveAt(customerId, at).map(Subscription::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SubscriptionView> subscription(UUID subscriptionId) {
        return subscriptions.findById(subscriptionId).map(Subscription::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SubscriptionView> subscriptions(UUID tenantId) {
        return subscriptions.findByTenantIdOrderByStartsAtDesc(tenantId).stream()
                .map(Subscription::toView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PlanView> plan(UUID planId) {
        return plansById.get(planId, id -> plans.findWithComponents(id).map(this::toView));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlanView> plans(UUID tenantId) {
        return plans.findAllWithComponents(tenantId).stream().map(this::toView).toList();
    }

    private PlanView toView(Plan plan) {
        List<UUID> meterIds =
                plan.getComponents().stream().map(PlanComponent::getMeterId).toList();
        Map<UUID, String> codes = meterIds.isEmpty()
                ? Map.of()
                : meters.findByIdIn(meterIds).stream()
                        .collect(Collectors.toMap(Meter::getId, Meter::getCode, (a, b) -> a));
        return plan.toView(codes);
    }

    private static String key(UUID tenantId, String s) {
        return tenantId + "|" + s;
    }
}
