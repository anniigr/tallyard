package io.tallyard.catalog.internal;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface MeterRepository extends JpaRepository<Meter, UUID> {
    List<Meter> findByTenantIdAndEventType(UUID tenantId, String eventType);

    List<Meter> findByTenantIdOrderByCode(UUID tenantId);

    Optional<Meter> findByTenantIdAndCode(UUID tenantId, String code);

    List<Meter> findByIdIn(List<UUID> ids);
}

interface CustomerRepository extends JpaRepository<Customer, UUID> {
    Optional<Customer> findByTenantIdAndExternalId(UUID tenantId, String externalId);

    List<Customer> findByTenantIdOrderByExternalId(UUID tenantId);
}

interface PlanRepository extends JpaRepository<Plan, UUID> {

    /** Loads components and tiers in one round trip to avoid N+1 selects when rating. */
    @Query("select distinct p from Plan p left join fetch p.components c left join fetch c.tiers where p.id = :id")
    Optional<Plan> findWithComponents(@Param("id") UUID id);

    @Query(
            "select distinct p from Plan p left join fetch p.components c left join fetch c.tiers where p.tenantId = :tenantId order by p.code")
    List<Plan> findAllWithComponents(@Param("tenantId") UUID tenantId);

    Optional<Plan> findByTenantIdAndCode(UUID tenantId, String code);
}

interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    @Query(
            "select s from Subscription s where s.customerId = :customerId and s.startsAt <= :at and (s.endsAt is null or s.endsAt > :at)")
    Optional<Subscription> findActiveAt(@Param("customerId") UUID customerId, @Param("at") Instant at);

    List<Subscription> findByTenantIdOrderByStartsAtDesc(UUID tenantId);

    Optional<Subscription> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByCustomerIdAndEndsAtIsNull(UUID customerId);
}
