package io.tallyard.outbox.internal;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.tallyard.outbox.OutboxMessage;
import io.tallyard.shared.config.TallyardProperties;
import io.tallyard.shared.json.Json;
import io.tallyard.shared.role.WorkerRole;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Polls unpublished outbox rows with {@code FOR UPDATE SKIP LOCKED} so several worker instances can
 * drain the table concurrently without coordination, publishes them to Kafka (waiting for acks) and
 * marks them published in the same transaction. If the process dies between the Kafka ack and the
 * commit, the rows are published again — at-least-once, which is why {@link OutboxMessage#eventId()}
 * is carried for consumer-side deduplication. Failed rows back off exponentially and are never
 * dropped; a row that keeps failing shows up in the {@code tallyard.outbox.backlog} gauge.
 */
@Component
@WorkerRole
class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final KafkaTemplate<String, String> kafka;
    private final Json json;
    private final Clock clock;
    private final TallyardProperties props;
    private final Counter published;
    private final Counter failed;
    private final AtomicLong backlog = new AtomicLong();

    OutboxPublisher(
            JdbcTemplate jdbc,
            TransactionTemplate tx,
            KafkaTemplate<String, String> kafka,
            Json json,
            Clock clock,
            TallyardProperties props,
            MeterRegistry registry) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.kafka = kafka;
        this.json = json;
        this.clock = clock;
        this.props = props;
        this.published = Counter.builder("tallyard.outbox.published").register(registry);
        this.failed = Counter.builder("tallyard.outbox.failed").register(registry);
        Gauge.builder("tallyard.outbox.backlog", backlog, AtomicLong::get)
                .description("Unpublished outbox rows at the last poll")
                .register(registry);
    }

    record Pending(
            UUID id,
            UUID tenantId,
            String aggregateType,
            UUID aggregateId,
            String eventType,
            String payload,
            Instant createdAt,
            int attempts) {}

    @Scheduled(fixedDelayString = "${tallyard.outbox.poll-interval}")
    public void publishPending() {
        int published;
        do {
            published = publishBatch();
        } while (published >= props.outbox().batchSize());
        Long remaining =
                jdbc.queryForObject("SELECT count(*) FROM outbox_event WHERE published_at IS NULL", Long.class);
        backlog.set(remaining == null ? 0 : remaining);
    }

    /** @return number of rows published in this batch */
    int publishBatch() {
        Integer n = tx.execute(status -> {
            List<Pending> batch = jdbc.query(
                    """
                    SELECT id, tenant_id, aggregate_type, aggregate_id, event_type, payload::text AS payload, created_at, attempts
                    FROM outbox_event
                    WHERE published_at IS NULL AND next_attempt_at <= ?
                    ORDER BY created_at
                    LIMIT ?
                    FOR UPDATE SKIP LOCKED
                    """,
                    (rs, i) -> new Pending(
                            rs.getObject("id", UUID.class),
                            rs.getObject("tenant_id", UUID.class),
                            rs.getString("aggregate_type"),
                            rs.getObject("aggregate_id", UUID.class),
                            rs.getString("event_type"),
                            rs.getString("payload"),
                            rs.getTimestamp("created_at").toInstant(),
                            rs.getInt("attempts")),
                    Timestamp.from(clock.instant()),
                    props.outbox().batchSize());
            if (batch.isEmpty()) {
                return 0;
            }
            List<CompletableFuture<?>> futures = new ArrayList<>(batch.size());
            for (Pending p : batch) {
                OutboxMessage message = new OutboxMessage(
                        p.id(),
                        p.tenantId(),
                        p.aggregateType(),
                        p.aggregateId(),
                        p.eventType(),
                        p.createdAt(),
                        p.payload());
                ProducerRecord<String, String> record = new ProducerRecord<>(
                        props.topics().billingEvents(), p.aggregateId().toString(), json.write(message));
                record.headers()
                        .add(new RecordHeader(
                                OutboxMessage.HEADER_EVENT_TYPE, p.eventType().getBytes(StandardCharsets.UTF_8)))
                        .add(new RecordHeader(
                                OutboxMessage.HEADER_EVENT_ID, p.id().toString().getBytes(StandardCharsets.UTF_8)));
                futures.add(kafka.send(record));
            }
            try {
                CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                        .get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                log.warn("Outbox publish failed for {} rows: {}", batch.size(), e.toString());
                for (Pending p : batch) {
                    Duration backoff = Duration.ofSeconds(Math.min(300, (long) Math.pow(2, Math.min(p.attempts(), 8))));
                    jdbc.update(
                            "UPDATE outbox_event SET attempts = attempts + 1, next_attempt_at = ? WHERE id = ?",
                            Timestamp.from(clock.instant().plus(backoff)),
                            p.id());
                }
                failed.increment(batch.size());
                return 0;
            }
            Timestamp now = Timestamp.from(clock.instant());
            jdbc.batchUpdate(
                    "UPDATE outbox_event SET published_at = ?, attempts = attempts + 1 WHERE id = ?",
                    batch,
                    batch.size(),
                    (ps, p) -> {
                        ps.setTimestamp(1, now);
                        ps.setObject(2, p.id());
                    });
            published.increment(batch.size());
            return batch.size();
        });
        return n == null ? 0 : n;
    }
}
