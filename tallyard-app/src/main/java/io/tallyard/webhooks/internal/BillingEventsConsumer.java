package io.tallyard.webhooks.internal;

import io.tallyard.outbox.OutboxMessage;
import io.tallyard.shared.json.Json;
import io.tallyard.shared.role.WorkerRole;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/** Consumes {@code billing.events.v1} and turns each event into durable webhook deliveries. */
@Component
@WorkerRole
class BillingEventsConsumer {

    private static final Logger log = LoggerFactory.getLogger(BillingEventsConsumer.class);

    private final WebhookService webhooks;
    private final Json json;

    BillingEventsConsumer(WebhookService webhooks, Json json) {
        this.webhooks = webhooks;
        this.json = json;
    }

    @KafkaListener(
            id = "webhooks",
            topics = "${tallyard.topics.billing-events}",
            groupId = "tallyard-webhooks",
            batch = "true")
    public void onBatch(List<ConsumerRecord<String, String>> records, Acknowledgment ack) {
        for (ConsumerRecord<String, String> record : records) {
            try {
                webhooks.enqueue(json.read(record.value(), OutboxMessage.class));
            } catch (RuntimeException e) {
                // Our own producer wrote this; a parse failure is a bug, not bad tenant input. Log and move on.
                log.error(
                        "Skipping unreadable billing event at {}-{}@{}",
                        record.topic(),
                        record.partition(),
                        record.offset(),
                        e);
            }
        }
        ack.acknowledge();
    }
}
