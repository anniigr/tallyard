package io.tallyard.metering.internal;

import io.tallyard.ingestion.UsageEventMessage;
import io.tallyard.metering.RejectionReason;
import io.tallyard.shared.config.TallyardProperties;
import io.tallyard.shared.json.Json;
import io.tallyard.shared.role.WorkerRole;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Batch consumer of {@code usage.events.v1}. Offsets are acknowledged only after the batch
 * transaction committed (at-least-once). Unparseable records are forwarded to the dead-letter topic
 * and recorded as rejections instead of poisoning the batch; infrastructure failures (database down)
 * propagate to the error handler, which retries the whole batch with backoff.
 */
@Component
@WorkerRole
class UsageEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(UsageEventConsumer.class);

    private final MeteringService metering;
    private final MeteringMetrics metrics;
    private final MeteringHooks hooks;
    private final KafkaTemplate<String, String> kafka;
    private final Json json;
    private final TallyardProperties props;

    UsageEventConsumer(
            MeteringService metering,
            MeteringMetrics metrics,
            MeteringHooks hooks,
            KafkaTemplate<String, String> kafka,
            Json json,
            TallyardProperties props) {
        this.metering = metering;
        this.metrics = metrics;
        this.hooks = hooks;
        this.kafka = kafka;
        this.json = json;
        this.props = props;
    }

    @KafkaListener(
            id = "metering",
            topics = "${tallyard.topics.usage-events}",
            groupId = "${tallyard.metering.consumer-group}",
            batch = "true")
    public void onBatch(List<ConsumerRecord<String, String>> records, Acknowledgment ack) {
        long started = System.nanoTime();
        List<UsageEventMessage> messages = new ArrayList<>(records.size());
        List<MeteringService.Poison> poison = new ArrayList<>(0);
        for (ConsumerRecord<String, String> record : records) {
            try {
                UsageEventMessage m = json.read(record.value(), UsageEventMessage.class);
                if (m.tenantId() == null
                        || m.eventId() == null
                        || m.eventType() == null
                        || m.subject() == null
                        || m.occurredAt() == null
                        || m.ingestedAt() == null) {
                    throw new IllegalArgumentException("required field missing");
                }
                messages.add(m);
            } catch (RuntimeException e) {
                UUID tenant = tenantHeader(record);
                String detail = "unparseable usage event: " + e.getMessage();
                log.warn(
                        "Dead-lettering record {}-{}@{}: {}",
                        record.topic(),
                        record.partition(),
                        record.offset(),
                        detail);
                deadLetter(record, detail);
                poison.add(new MeteringService.Poison(record.key(), record.value(), tenant, detail));
                metrics.rejected(RejectionReason.SCHEMA_INVALID);
            }
        }

        MeteringService.BatchResult result = metering.process(messages, poison);
        hooks.afterCommit();
        ack.acknowledge();
        metrics.record(result, System.nanoTime() - started);
    }

    private void deadLetter(ConsumerRecord<String, String> record, String reason) {
        ProducerRecord<String, String> dlt =
                new ProducerRecord<>(props.topics().usageEventsDlt(), record.key(), record.value());
        record.headers().forEach(h -> dlt.headers().add(h));
        dlt.headers().add(new RecordHeader("ty-dlt-reason", reason.getBytes(StandardCharsets.UTF_8)));
        dlt.headers()
                .add(new RecordHeader(
                        "ty-dlt-source",
                        (record.topic() + "-" + record.partition() + "@" + record.offset())
                                .getBytes(StandardCharsets.UTF_8)));
        kafka.send(dlt);
    }

    private static UUID tenantHeader(ConsumerRecord<String, String> record) {
        Header h = record.headers().lastHeader(UsageEventMessage.HEADER_TENANT);
        if (h == null) {
            return null;
        }
        try {
            return UUID.fromString(new String(h.value(), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
