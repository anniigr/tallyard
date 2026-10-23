package io.tallyard.outbox.internal;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {}
