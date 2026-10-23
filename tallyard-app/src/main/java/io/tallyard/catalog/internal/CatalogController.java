package io.tallyard.catalog.internal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.tallyard.catalog.Aggregation;
import io.tallyard.catalog.BillingInterval;
import io.tallyard.catalog.CatalogCommands;
import io.tallyard.catalog.CustomerView;
import io.tallyard.catalog.MeterView;
import io.tallyard.catalog.PlanView;
import io.tallyard.catalog.PricingModel;
import io.tallyard.catalog.SubscriptionView;
import io.tallyard.shared.error.NotFoundException;
import io.tallyard.shared.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
@Tag(name = "Catalog", description = "Meters, customers, plans and subscriptions")
class CatalogController {

    private final CatalogService catalog;

    CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    // ---------------------------------------------------------------- meters

    record CreateMeterRequest(
            @NotBlank @Pattern(regexp = "[a-z][a-z0-9_]{1,63}")
            String code,

            @NotBlank @Size(max = 120) String name,

            @NotBlank @Pattern(regexp = "[a-zA-Z][a-zA-Z0-9_.]{0,63}")
            String eventType,

            @NotNull Aggregation aggregation,
            @Size(max = 64) String valueProperty,
            @NotBlank @Size(max = 32) String unit,
            Duration acceptanceWindow) {}

    @Operation(summary = "Create a meter")
    @PostMapping("/meters")
    @PreAuthorize("@scopes.has('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    MeterView createMeter(@Valid @RequestBody CreateMeterRequest r) {
        return catalog.createMeter(
                TenantContext.tenantId(),
                new CatalogCommands.NewMeter(
                        r.code(),
                        r.name(),
                        r.eventType(),
                        r.aggregation(),
                        r.valueProperty(),
                        r.unit(),
                        r.acceptanceWindow()));
    }

    @Operation(summary = "List meters")
    @GetMapping("/meters")
    @PreAuthorize("@scopes.has('READ')")
    List<MeterView> meters() {
        return catalog.meters(TenantContext.tenantId());
    }

    // ---------------------------------------------------------------- customers

    record CreateCustomerRequest(
            @NotBlank @Size(max = 128) String externalId,
            @NotBlank @Size(max = 200) String name) {}

    @Operation(summary = "Create a customer (the subject of usage events)")
    @PostMapping("/customers")
    @PreAuthorize("@scopes.has('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    CustomerView createCustomer(@Valid @RequestBody CreateCustomerRequest r) {
        return catalog.createCustomer(TenantContext.tenantId(), r.externalId(), r.name());
    }

    @Operation(summary = "List customers")
    @GetMapping("/customers")
    @PreAuthorize("@scopes.has('READ')")
    List<CustomerView> customers() {
        return catalog.customers(TenantContext.tenantId());
    }

    @Operation(summary = "Get a customer by external id")
    @GetMapping("/customers/{externalId}")
    @PreAuthorize("@scopes.has('READ')")
    CustomerView customer(@PathVariable String externalId) {
        return catalog.customerByExternalId(TenantContext.tenantId(), externalId)
                .orElseThrow(() -> new NotFoundException("Customer", externalId));
    }

    // ---------------------------------------------------------------- plans

    record TierRequest(
            @DecimalMin("0") BigDecimal upTo,
            @DecimalMin("0") BigDecimal unitPrice,
            @DecimalMin("0") BigDecimal flatAmount) {}

    record ComponentRequest(
            @NotBlank String meterCode,
            @NotNull PricingModel pricingModel,
            @DecimalMin("0") BigDecimal includedUnits,
            @DecimalMin(value = "0", inclusive = false) BigDecimal packageSize,
            @DecimalMin("0") BigDecimal hardLimit,
            @Valid List<TierRequest> tiers) {}

    record CreatePlanRequest(
            @NotBlank @Pattern(regexp = "[a-z][a-z0-9_-]{1,63}")
            String code,

            @NotBlank @Size(max = 120) String name,
            @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
            @NotNull BillingInterval.Unit intervalUnit,
            @Min(1) int intervalLength,
            @Min(0) int graceSeconds,
            @DecimalMin("0") BigDecimal minimumAmount,
            @NotEmpty @Valid List<ComponentRequest> components) {}

    @Operation(summary = "Create a plan with its price components")
    @PostMapping("/plans")
    @PreAuthorize("@scopes.has('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    PlanView createPlan(@Valid @RequestBody CreatePlanRequest r) {
        List<CatalogCommands.NewComponent> components = r.components().stream()
                .map(c -> new CatalogCommands.NewComponent(
                        c.meterCode(),
                        c.pricingModel(),
                        c.includedUnits(),
                        c.packageSize(),
                        c.hardLimit(),
                        c.tiers() == null
                                ? List.of()
                                : c.tiers().stream()
                                        .map(t -> new CatalogCommands.NewTier(t.upTo(), t.unitPrice(), t.flatAmount()))
                                        .toList()))
                .toList();
        return catalog.createPlan(
                TenantContext.tenantId(),
                new CatalogCommands.NewPlan(
                        r.code(),
                        r.name(),
                        Currency.getInstance(r.currency()),
                        new BillingInterval(r.intervalUnit(), r.intervalLength()),
                        r.graceSeconds(),
                        r.minimumAmount(),
                        components));
    }

    @Operation(summary = "List plans")
    @GetMapping("/plans")
    @PreAuthorize("@scopes.has('READ')")
    List<PlanView> plans() {
        return catalog.plans(TenantContext.tenantId());
    }

    // ---------------------------------------------------------------- subscriptions

    record SubscribeRequest(
            @NotBlank String customerExternalId, @NotBlank String planCode, Instant startsAt) {}

    @Operation(summary = "Subscribe a customer to a plan; opens the first billing period")
    @PostMapping("/subscriptions")
    @PreAuthorize("@scopes.has('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    SubscriptionView subscribe(@Valid @RequestBody SubscribeRequest r) {
        return catalog.subscribe(TenantContext.tenantId(), r.customerExternalId(), r.planCode(), r.startsAt());
    }

    @Operation(summary = "List subscriptions")
    @GetMapping("/subscriptions")
    @PreAuthorize("@scopes.has('READ')")
    List<SubscriptionView> subscriptions() {
        return catalog.subscriptions(TenantContext.tenantId());
    }

    @Operation(summary = "End a subscription")
    @PostMapping("/subscriptions/{id}/end")
    @PreAuthorize("@scopes.has('ADMIN')")
    SubscriptionView end(@PathVariable UUID id, @RequestParam(required = false) Instant at) {
        return catalog.endSubscription(TenantContext.tenantId(), id, at);
    }
}
