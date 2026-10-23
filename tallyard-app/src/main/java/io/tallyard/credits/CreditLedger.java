package io.tallyard.credits;

import io.tallyard.shared.money.Money;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface CreditLedger {

    /** Remaining balance across unexpired grants in the given currency, as of {@code at}. */
    Money balance(UUID customerId, java.util.Currency currency, Instant at);

    boolean hasGrants(UUID customerId, java.util.Currency currency);

    /**
     * Consumes up to {@code amount} from the customer's grants in expiry-then-priority order, recording
     * one ledger entry per grant touched. Must be called inside the rating transaction.
     *
     * @return how much was actually applied (≤ amount) and the per-grant entries
     */
    Application apply(UUID tenantId, UUID customerId, Money amount, UUID invoiceId, Instant at);

    record Application(Money applied, List<Entry> entries) {}

    record Entry(UUID grantId, Money amount) {}

    /** Grants prepaid credit; returns the grant id. */
    UUID grant(UUID tenantId, String customerExternalId, Money amount, int priority, Instant expiresAt);
}
