package io.tallyard.billing;

import io.tallyard.shared.money.Money;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read side used by dashboards and entitlements. */
public interface BillingQueries {

    List<BillingPeriodView> periodsForCustomer(UUID customerId);

    Optional<InvoiceView> invoiceForPeriod(UUID periodId);

    List<InvoiceView> invoicesForTenant(UUID tenantId, int limit);

    /** Rates the open period's current usage without persisting anything. */
    Money liveEstimate(UUID periodId);
}
