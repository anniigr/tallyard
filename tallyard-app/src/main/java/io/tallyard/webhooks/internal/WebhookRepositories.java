package io.tallyard.webhooks.internal;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

interface WebhookEndpointRepository extends JpaRepository<WebhookEndpoint, UUID> {
    List<WebhookEndpoint> findByTenantIdAndActiveTrue(UUID tenantId);

    List<WebhookEndpoint> findByTenantIdOrderByCreatedAt(UUID tenantId);

    Optional<WebhookEndpoint> findByIdAndTenantId(UUID id, UUID tenantId);
}

interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query(
            "select d from WebhookDelivery d where d.state = io.tallyard.webhooks.internal.WebhookDelivery$State.PENDING and d.nextAttemptAt <= :now order by d.nextAttemptAt")
    List<WebhookDelivery> lockDue(@Param("now") Instant now, Limit limit);

    List<WebhookDelivery> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Limit limit);

    Optional<WebhookDelivery> findByIdAndTenantId(UUID id, UUID tenantId);

    long countByState(WebhookDelivery.State state);
}
