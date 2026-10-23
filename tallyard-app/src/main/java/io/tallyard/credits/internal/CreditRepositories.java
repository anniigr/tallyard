package io.tallyard.credits.internal;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface CreditGrantRepository extends JpaRepository<CreditGrant, UUID> {

    /**
     * Locks the customer's usable grants ({@code SELECT ... FOR UPDATE}) in application order:
     * soonest expiry first, then priority, then age. Two concurrent rating transactions for the same
     * customer therefore serialise here instead of both reading the same balance.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select g from CreditGrant g
            where g.customerId = :customerId and g.currency = :currency
              and (g.expiresAt is null or g.expiresAt > :at)
              and g.amountConsumed < g.amountGranted
            order by g.expiresAt asc nulls last, g.priority asc, g.createdAt asc
            """)
    List<CreditGrant> lockUsable(
            @Param("customerId") UUID customerId, @Param("currency") String currency, @Param("at") Instant at);

    @Query("""
            select g from CreditGrant g
            where g.customerId = :customerId and g.currency = :currency
            order by g.expiresAt asc nulls last, g.priority asc, g.createdAt asc
            """)
    List<CreditGrant> findAllFor(@Param("customerId") UUID customerId, @Param("currency") String currency);

    boolean existsByCustomerIdAndCurrency(UUID customerId, String currency);

    List<CreditGrant> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}

interface CreditLedgerEntryRepository extends JpaRepository<CreditLedgerEntry, UUID> {}
