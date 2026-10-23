package io.tallyard.ingestion.internal;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.tallyard.ingestion.UsageEventMessage;
import io.tallyard.shared.config.TallyardProperties;
import io.tallyard.shared.error.RateLimitedException;
import io.tallyard.shared.error.ServiceUnavailableException;
import io.tallyard.shared.json.Json;
import io.tallyard.shared.tenant.TenantPrincipal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Validates a batch, publishes accepted events to Kafka with {@code acks=all} and an idempotent
 * producer, and waits for the broker acknowledgements before answering 202. A batch is therefore
 * durable when the client sees the response. If Kafka cannot acknowledge within the timeout the
 * whole call fails with 503 + Retry-After; clients retry with the same event ids and the consumer
 * deduplicates, so retries are always safe.
 */
@Service
class IngestService {

    private static final Logger log = LoggerFactory.getLogger(IngestService.class);

    private final KafkaTemplate<String, String> kafka;
    private final Json json;
    private final Clock clock;
    private final TallyardProperties props;
    private final EventValidator validator;
    private final Cache<UUID, TokenBucket> buckets;
    private final Counter accepted;
    private final Counter invalid;
    private final Counter rateLimited;
    private final Timer publishTimer;

    IngestService(
            KafkaTemplate<String, String> kafka,
            Json json,
            Clock clock,
            TallyardProperties props,
            MeterRegistry registry) {
        this.kafka = kafka;
        this.json = json;
        this.clock = clock;
        this.props = props;
        this.validator = new EventValidator(clock, props.ingestion().maxFutureSkew());
        this.buckets = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterAccess(Duration.ofMinutes(10))
                .build();
        this.accepted = Counter.builder("tallyard.ingest.events")
                .tag("result", "accepted")
                .register(registry);
        this.invalid = Counter.builder("tallyard.ingest.events")
                .tag("result", "invalid")
                .register(registry);
        this.rateLimited = Counter.builder("tallyard.ingest.rate_limited").register(registry);
        this.publishTimer = Timer.builder("tallyard.ingest.publish")
                .description("Time to get broker acks for a whole batch")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }

    IngestResult ingest(TenantPrincipal principal, List<IncomingEvent> events) {
        long waitNanos = buckets.get(
                        principal.apiKeyId(),
                        k -> new TokenBucket(props.ingestion().rateLimitPerKeyPerSecond()))
                .tryConsume(events.size());
        if (waitNanos > 0) {
            rateLimited.increment();
            throw new RateLimitedException(Duration.ofNanos(waitNanos).plusMillis(1));
        }

        Instant ingestedAt = clock.instant();
        List<IngestResult.EventStatus> statuses = new ArrayList<>(events.size());
        List<ProducerRecord<String, String>> records = new ArrayList<>(events.size());
        for (IncomingEvent event : events) {
            List<String> errors = validator.validate(event);
            if (!errors.isEmpty()) {
                statuses.add(IngestResult.EventStatus.invalid(event.eventId(), errors));
                continue;
            }
            UsageEventMessage message = new UsageEventMessage(
                    principal.tenantId(),
                    event.eventId(),
                    event.eventType(),
                    event.subject(),
                    event.occurredAt(),
                    ingestedAt,
                    event.properties() == null ? Map.of() : event.properties());
            ProducerRecord<String, String> record =
                    new ProducerRecord<>(props.topics().usageEvents(), message.partitionKey(), json.write(message));
            record.headers()
                    .add(new RecordHeader(
                            UsageEventMessage.HEADER_SCHEMA_VERSION,
                            Integer.toString(UsageEventMessage.SCHEMA_VERSION).getBytes(StandardCharsets.UTF_8)))
                    .add(new RecordHeader(
                            UsageEventMessage.HEADER_TENANT,
                            principal.tenantId().toString().getBytes(StandardCharsets.UTF_8)));
            records.add(record);
            statuses.add(IngestResult.EventStatus.accepted(event.eventId()));
        }

        if (!records.isEmpty()) {
            publish(records);
        }
        long acceptedCount = statuses.stream()
                .filter(s -> s.status() == IngestResult.Status.ACCEPTED)
                .count();
        accepted.increment(acceptedCount);
        invalid.increment(statuses.size() - acceptedCount);
        return new IngestResult((int) acceptedCount, (int) (statuses.size() - acceptedCount), statuses);
    }

    private void publish(List<ProducerRecord<String, String>> records) {
        Timer.Sample sample = Timer.start();
        List<CompletableFuture<?>> futures = new ArrayList<>(records.size());
        for (ProducerRecord<String, String> record : records) {
            futures.add(kafka.send(record));
        }
        try {
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                    .get(props.ingestion().kafkaSendTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.warn(
                    "Kafka did not acknowledge {} records within {}",
                    records.size(),
                    props.ingestion().kafkaSendTimeout());
            throw unavailable(e);
        } catch (ExecutionException e) {
            log.warn(
                    "Kafka publish failed: {}",
                    e.getCause() == null ? e.toString() : e.getCause().toString());
            throw unavailable(e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unavailable(e);
        } finally {
            sample.stop(publishTimer);
        }
    }

    private static ServiceUnavailableException unavailable(Throwable cause) {
        return new ServiceUnavailableException(
                "kafka_unavailable",
                "Event log temporarily unavailable; retry with the same event ids",
                Duration.ofSeconds(5),
                cause);
    }
}
