package io.tallyard.billing.internal;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

interface BillingPeriodRepository extends JpaRepository<BillingPeriod, UUID> {

    /** {@code SELECT ... FOR SHARE}: metering holds this while writing usage into the period. */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query(
            "select p from BillingPeriod p where p.subscriptionId = :subscriptionId and p.startsAt <= :at and p.endsAt > :at")
    Optional<BillingPeriod> lockShareCovering(@Param("subscriptionId") UUID subscriptionId, @Param("at") Instant at);

    /** {@code SELECT ... FOR UPDATE}: taken by rating; waits for in-flight metering transactions. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from BillingPeriod p where p.id = :id")
    Optional<BillingPeriod> lockForUpdate(@Param("id") UUID id);

    @Query(
            "select p from BillingPeriod p where p.subscriptionId = :subscriptionId and p.state = io.tallyard.billing.PeriodState.OPEN")
    Optional<BillingPeriod> findOpen(@Param("subscriptionId") UUID subscriptionId);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query(
            "select p from BillingPeriod p where p.subscriptionId = :subscriptionId and p.state = io.tallyard.billing.PeriodState.OPEN")
    Optional<BillingPeriod> lockShareOpen(@Param("subscriptionId") UUID subscriptionId);

    /**
     * Work-queue pattern: {@code FOR UPDATE SKIP LOCKED} (lock timeout -2 in Hibernate) lets several
     * worker instances pick disjoint due periods without a distributed lock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query(
            "select p from BillingPeriod p where p.state = io.tallyard.billing.PeriodState.OPEN and p.endsAt <= :now order by p.endsAt")
    List<BillingPeriod> lockDueForClosing(@Param("now") Instant now, org.springframework.data.domain.Limit limit);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query(
            "select p from BillingPeriod p where p.state = io.tallyard.billing.PeriodState.CLOSING and p.graceUntil <= :now order by p.graceUntil")
    List<BillingPeriod> lockDueForRating(@Param("now") Instant now, org.springframework.data.domain.Limit limit);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query(
            "select p from BillingPeriod p where p.state = io.tallyard.billing.PeriodState.RATED and p.ratedAt <= :before order by p.ratedAt")
    List<BillingPeriod> lockDueForFinalizing(
            @Param("before") Instant before, org.springframework.data.domain.Limit limit);

    List<BillingPeriod> findByCustomerIdOrderByStartsAtDesc(UUID customerId);

    List<BillingPeriod> findByTenantIdOrderByStartsAtDesc(UUID tenantId);

    Optional<BillingPeriod> findByIdAndTenantId(UUID id, UUID tenantId);
}

interface InvoiceDraftRepository extends JpaRepository<InvoiceDraft, UUID> {

    @Query("select distinct i from InvoiceDraft i left join fetch i.lines where i.periodId = :periodId")
    Optional<InvoiceDraft> findByPeriodIdWithLines(@Param("periodId") UUID periodId);

    @Query(
            "select distinct i from InvoiceDraft i left join fetch i.lines where i.tenantId = :tenantId order by i.createdAt desc")
    List<InvoiceDraft> findByTenantWithLines(
            @Param("tenantId") UUID tenantId, org.springframework.data.domain.Limit limit);
}
