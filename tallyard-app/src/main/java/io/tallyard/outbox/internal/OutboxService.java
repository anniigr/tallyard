package io.tallyard.outbox.internal;

import io.tallyard.outbox.Outbox;
import io.tallyard.shared.id.Ids;
import io.tallyard.shared.json.Json;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class OutboxService implements Outbox {

    private final OutboxEventRepository repository;
    private final Json json;
    private final Clock clock;

    OutboxService(OutboxEventRepository repository, Json json, Clock clock) {
        this.repository = repository;
        this.json = json;
        this.clock = clock;
    }

    /** MANDATORY: an outbox row that is not part of the caller's transaction would defeat the pattern. */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID append(UUID tenantId, String aggregateType, UUID aggregateId, String eventType, Object payload) {
        UUID id = Ids.newId(clock);
        repository.save(new OutboxEvent(
                id, tenantId, aggregateType, aggregateId, eventType, json.write(payload), clock.instant()));
        return id;
    }
}
