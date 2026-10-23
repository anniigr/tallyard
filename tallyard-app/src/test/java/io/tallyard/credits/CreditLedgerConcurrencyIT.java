package io.tallyard.credits;

import static org.assertj.core.api.Assertions.assertThat;

import io.tallyard.catalog.BillingInterval;
import io.tallyard.shared.money.Money;
import io.tallyard.support.AbstractIntegrationTest;
import io.tallyard.support.TestTenant;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** C1/C2: concurrent consumption never overdraws; grants are consumed in expiry/priority order. */
class CreditLedgerConcurrencyIT extends AbstractIntegrationTest {

    static final Currency USD = Currency.getInstance("USD");

    @Autowired
    TestTenant tenants;

    @Autowired
    CreditLedger credits;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void sixteenConcurrentApplicationsNeverExceedTheGrantedTotal() throws Exception {
        TestTenant.Fixture fx = tenants.create();
        tenants.standardMeters(fx.tenantId());
        tenants.standardPlan(fx.tenantId(), "std", new BillingInterval(BillingInterval.Unit.DAYS, 30), 0);
        var sub =
                tenants.customer(fx.tenantId(), "prepaid", "std", Instant.now().minusSeconds(60));
        UUID customerId = sub.customerId();
        credits.grant(fx.tenantId(), "prepaid", Money.of("100.00", "USD"), 0, null);
        Instant now = Instant.now();

        ExecutorService pool = Executors.newFixedThreadPool(16);
        List<CompletableFuture<Money>> futures = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            futures.add(CompletableFuture.supplyAsync(
                    () -> tx.execute(status -> {
                        UUID invoiceId = insertInvoiceShell(fx.tenantId(), customerId, sub.id());
                        return credits.apply(fx.tenantId(), customerId, Money.of("9.00", "USD"), invoiceId, now)
                                .applied();
                    }),
                    pool));
        }
        Money total = Money.zero(USD);
        for (CompletableFuture<Money> f : futures) {
            total = total.plus(f.get());
        }
        pool.shutdown();
        // 16 × 9 = 144 requested, only 100 available.
        assertThat(total.amount()).isEqualByComparingTo("100.00");
        assertThat(credits.balance(customerId, USD, now).amount()).isEqualByComparingTo("0");
        assertThat(jdbc.queryForObject(
                        "select amount_consumed from credit_grant where customer_id = ?",
                        java.math.BigDecimal.class,
                        customerId))
                .isEqualByComparingTo("100.00");
    }

    @Test
    void grantsAreConsumedSoonestExpiryThenPriority() {
        TestTenant.Fixture fx = tenants.create();
        tenants.standardMeters(fx.tenantId());
        tenants.standardPlan(fx.tenantId(), "std", new BillingInterval(BillingInterval.Unit.DAYS, 30), 0);
        var sub =
                tenants.customer(fx.tenantId(), "ordered", "std", Instant.now().minusSeconds(60));
        Instant now = Instant.now();
        UUID later = credits.grant(fx.tenantId(), "ordered", Money.of("50", "USD"), 0, now.plusSeconds(86400 * 30));
        UUID soon = credits.grant(fx.tenantId(), "ordered", Money.of("10", "USD"), 5, now.plusSeconds(3600));
        UUID never = credits.grant(fx.tenantId(), "ordered", Money.of("20", "USD"), 0, null);

        CreditLedger.Application applied = tx.execute(s -> credits.apply(
                fx.tenantId(),
                sub.customerId(),
                Money.of("15", "USD"),
                insertInvoiceShell(fx.tenantId(), sub.customerId(), sub.id()),
                now));
        assertThat(applied.applied().amount()).isEqualByComparingTo("15");
        assertThat(applied.entries()).hasSize(2);
        assertThat(applied.entries().get(0).grantId()).isEqualTo(soon);
        assertThat(applied.entries().get(0).amount().amount()).isEqualByComparingTo("10");
        assertThat(applied.entries().get(1).grantId()).isEqualTo(later);
        assertThat(applied.entries().get(1).amount().amount()).isEqualByComparingTo("5");
        assertThat(credits.balance(sub.customerId(), USD, now).amount()).isEqualByComparingTo("65");
        assertThat(never).isNotNull();
    }

    /** Ledger entries reference an invoice; create a minimal draft row for the FK. */
    private UUID insertInvoiceShell(UUID tenantId, UUID customerId, UUID subscriptionId) {
        UUID periodId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update(
                "insert into billing_period (id, tenant_id, subscription_id, customer_id, starts_at, ends_at, state, grace_until) values (?,?,?,?,?,?,'RATED',?)",
                periodId,
                tenantId,
                subscriptionId,
                customerId,
                java.sql.Timestamp.from(now.minusSeconds(7200 + periodId.hashCode() % 1000 + 1000)),
                java.sql.Timestamp.from(now.minusSeconds(3600 + periodId.hashCode() % 1000 + 1000)),
                java.sql.Timestamp.from(now));
        jdbc.update(
                "insert into invoice_draft (id, tenant_id, period_id, customer_id, currency, subtotal, credits_applied, total, reproducibility_hash) values (?,?,?,?,'USD',0,0,0,?)",
                invoiceId,
                tenantId,
                periodId,
                customerId,
                "0".repeat(64));
        return invoiceId;
    }
}
