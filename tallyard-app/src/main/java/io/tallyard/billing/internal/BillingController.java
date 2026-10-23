package io.tallyard.billing.internal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.tallyard.billing.BillingPeriodView;
import io.tallyard.billing.InvoiceView;
import io.tallyard.billing.ReplayReport;
import io.tallyard.catalog.CatalogQueries;
import io.tallyard.shared.error.NotFoundException;
import io.tallyard.shared.money.Money;
import io.tallyard.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
@Tag(name = "Billing", description = "Billing periods, invoice drafts and replay")
class BillingController {

    private final BillingService billing;
    private final CatalogQueries catalog;

    BillingController(BillingService billing, CatalogQueries catalog) {
        this.billing = billing;
        this.catalog = catalog;
    }

    @Operation(summary = "List billing periods, optionally for one customer")
    @GetMapping("/periods")
    @PreAuthorize("@scopes.has('READ')")
    List<BillingPeriodView> periods(@RequestParam(required = false) String customer) {
        UUID tenantId = TenantContext.tenantId();
        if (customer != null) {
            return catalog.customerByExternalId(tenantId, customer)
                    .map(c -> billing.periodsForCustomer(c.id()))
                    .orElseThrow(() -> new NotFoundException("Customer", customer));
        }
        return billing.periodsForTenant(tenantId);
    }

    @Operation(summary = "Get a billing period")
    @GetMapping("/periods/{id}")
    @PreAuthorize("@scopes.has('READ')")
    BillingPeriodView period(@PathVariable UUID id) {
        return billing.view(TenantContext.tenantId(), id);
    }

    record EstimateResponse(UUID periodId, String currency, BigDecimal subtotal) {}

    @Operation(summary = "Rate the period's current usage without persisting (live estimate)")
    @GetMapping("/periods/{id}/estimate")
    @PreAuthorize("@scopes.has('READ')")
    EstimateResponse estimate(@PathVariable UUID id) {
        billing.view(TenantContext.tenantId(), id);
        Money m = billing.liveEstimate(id);
        return new EstimateResponse(id, m.currencyCode(), m.rounded().amount());
    }

    @Operation(summary = "Close an OPEN period now (starts the grace window; opens the next period)")
    @PostMapping("/periods/{id}/close")
    @PreAuthorize("@scopes.has('ADMIN')")
    BillingPeriodView close(@PathVariable UUID id) {
        return billing.close(TenantContext.tenantId(), id);
    }

    @Operation(summary = "Rate a period now (closes it first if still OPEN) and produce the invoice draft")
    @PostMapping("/periods/{id}/rate")
    @PreAuthorize("@scopes.has('ADMIN')")
    InvoiceView rate(@PathVariable UUID id) {
        return billing.rateNow(TenantContext.tenantId(), id);
    }

    @Operation(summary = "Finalize a RATED period")
    @PostMapping("/periods/{id}/finalize")
    @PreAuthorize("@scopes.has('ADMIN')")
    BillingPeriodView finalizePeriod(@PathVariable UUID id) {
        return billing.finalizePeriod(TenantContext.tenantId(), id);
    }

    @Operation(
            summary = "Replay a rated period from the immutable event log and compare with the stored invoice",
            description = "Recomputes quantities from usage_event as of the original rating time and re-rates them. "
                    + "matches=true proves the stored invoice is exactly reproducible from the raw events.")
    @PostMapping("/periods/{id}/replay")
    @PreAuthorize("@scopes.has('ADMIN')")
    ReplayReport replay(@PathVariable UUID id) {
        return billing.replay(TenantContext.tenantId(), id);
    }

    @Operation(summary = "Get the invoice draft of a period")
    @GetMapping("/periods/{id}/invoice")
    @PreAuthorize("@scopes.has('READ')")
    InvoiceView invoice(@PathVariable UUID id) {
        billing.view(TenantContext.tenantId(), id);
        return billing.invoiceForPeriod(id).orElseThrow(() -> new NotFoundException("Invoice for period", id));
    }

    @Operation(summary = "List recent invoice drafts")
    @GetMapping("/invoices")
    @PreAuthorize("@scopes.has('READ')")
    List<InvoiceView> invoices(@RequestParam(defaultValue = "50") int limit) {
        return billing.invoicesForTenant(TenantContext.tenantId(), Math.min(Math.max(limit, 1), 500));
    }
}
