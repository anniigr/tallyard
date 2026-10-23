package io.tallyard.credits.internal;

import io.tallyard.catalog.CatalogQueries;
import io.tallyard.catalog.CustomerView;
import io.tallyard.credits.CreditLedger;
import io.tallyard.shared.error.NotFoundException;
import io.tallyard.shared.error.ValidationException;
import io.tallyard.shared.id.Ids;
import io.tallyard.shared.money.Money;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class CreditService implements CreditLedger {

    private final CreditGrantRepository grants;
    private final CreditLedgerEntryRepository entries;
    private final CatalogQueries catalog;
    private final Clock clock;

    CreditService(
            CreditGrantRepository grants, CreditLedgerEntryRepository entries, CatalogQueries catalog, Clock clock) {
        this.grants = grants;
        this.entries = entries;
        this.catalog = catalog;
        this.clock = clock;
    }

    @Override
    @Transactional
    public UUID grant(UUID tenantId, String customerExternalId, Money amount, int priority, Instant expiresAt) {
        return grantInternal(tenantId, customerExternalId, amount.currency(), amount.amount(), priority, expiresAt)
                .getId();
    }

    @Transactional
    CreditGrant grantInternal(
            UUID tenantId,
            String customerExternalId,
            Currency currency,
            BigDecimal amount,
            int priority,
            Instant expiresAt) {
        if (amount == null || amount.signum() <= 0) {
            throw new ValidationException("credit.amount", "Grant amount must be positive");
        }
        CustomerView customer = catalog.customerByExternalId(tenantId, customerExternalId)
                .orElseThrow(() -> new NotFoundException("Customer", customerExternalId));
        CreditGrant grant = new CreditGrant(
                Ids.newId(clock),
                tenantId,
                customer.id(),
                currency.getCurrencyCode(),
                amount,
                priority,
                expiresAt,
                clock.instant());
        return grants.save(grant);
    }

    @Transactional(readOnly = true)
    List<CreditGrant> grantsFor(UUID tenantId, String customerExternalId) {
        CustomerView customer = catalog.customerByExternalId(tenantId, customerExternalId)
                .orElseThrow(() -> new NotFoundException("Customer", customerExternalId));
        return grants.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .filter(g -> g.getCustomerId().equals(customer.id()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Money balance(UUID customerId, Currency currency, Instant at) {
        BigDecimal total = grants.findAllFor(customerId, currency.getCurrencyCode()).stream()
                .filter(g -> g.isUsableAt(at))
                .map(CreditGrant::remaining)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return Money.of(total, currency);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasGrants(UUID customerId, Currency currency) {
        return grants.existsByCustomerIdAndCurrency(customerId, currency.getCurrencyCode());
    }

    @Override
    @Transactional
    public Application apply(UUID tenantId, UUID customerId, Money amount, UUID invoiceId, Instant at) {
        if (!amount.isPositive()) {
            return new Application(Money.zero(amount.currency()), List.of());
        }
        BigDecimal wanted = amount.amount();
        List<Entry> applied = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (CreditGrant grant : grants.lockUsable(customerId, amount.currencyCode(), at)) {
            if (wanted.signum() <= 0) {
                break;
            }
            BigDecimal taken = grant.consumeUpTo(wanted);
            if (taken.signum() > 0) {
                entries.save(new CreditLedgerEntry(Ids.newId(clock), grant.getId(), invoiceId, taken, clock.instant()));
                applied.add(new Entry(grant.getId(), Money.of(taken, amount.currency())));
                wanted = wanted.subtract(taken);
                total = total.add(taken);
            }
        }
        grants.flush();
        return new Application(Money.of(total, amount.currency()), applied);
    }
}
