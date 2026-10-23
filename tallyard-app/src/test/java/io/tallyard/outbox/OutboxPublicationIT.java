package io.tallyard.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.tallyard.catalog.BillingInterval;
import io.tallyard.support.AbstractIntegrationTest;
import io.tallyard.support.ApiClient;
import io.tallyard.support.Containers;
import io.tallyard.support.TestTenant;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/** B8/E1/E2: outbox rows are published once per row, in order per aggregate, and only after commit. */
class OutboxPublicationIT extends AbstractIntegrationTest {

    @Autowired
    TestTenant tenants;

    @Autowired
    Outbox outbox;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ObjectMapper json;

    @LocalServerPort
    int port;

    @Test
    void ratingEmitsExactlyOnePeriodRatedAndOneInvoiceDraftedEventOnKafka() {
        TestTenant.Fixture fx = tenants.create();
        tenants.standardMeters(fx.tenantId());
        tenants.standardPlan(fx.tenantId(), "std", new BillingInterval(BillingInterval.Unit.DAYS, 30), 0);
        tenants.customer(fx.tenantId(), "pub", "std", Instant.now().minusSeconds(60));
        ApiClient admin = new ApiClient(port, fx.adminKey(), json);
        UUID period = admin.openPeriodId("pub");
        admin.post("/v1/periods/" + period + "/rate", null);

        List<OutboxMessage> seen = consumeTenant(fx.tenantId(), Duration.ofSeconds(20), 4);
        List<String> types = seen.stream().map(OutboxMessage::eventType).toList();
        assertThat(types).containsOnlyOnce("period.rated", "invoice.drafted");
        assertThat(types.stream().filter("period.opened"::equals).count()).isEqualTo(2); // first period + successor
        // outbox rows are marked published
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(jdbc.queryForObject(
                                "select count(*) from outbox_event where tenant_id = ? and published_at is null",
                                Long.class,
                                fx.tenantId()))
                        .isZero());
    }

    @Test
    void rolledBackTransactionsNeverPublish() {
        TestTenant.Fixture fx = tenants.create();
        UUID aggregate = UUID.randomUUID();
        try {
            tx.executeWithoutResult(status -> {
                outbox.append(fx.tenantId(), "Test", aggregate, "test.rolled_back", Map.of("x", 1));
                throw new IllegalStateException("boom");
            });
        } catch (IllegalStateException expected) {
            // rolled back
        }
        tx.executeWithoutResult(
                status -> outbox.append(fx.tenantId(), "Test", aggregate, "test.committed", Map.of("x", 2)));
        List<OutboxMessage> seen = consumeTenant(fx.tenantId(), Duration.ofSeconds(15), 1);
        assertThat(seen).extracting(OutboxMessage::eventType).containsExactly("test.committed");
    }

    @Test
    void appendOutsideATransactionIsRefused() {
        TestTenant.Fixture fx = tenants.create();
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> outbox.append(fx.tenantId(), "Test", UUID.randomUUID(), "test.no_tx", Map.of()))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
    }

    private List<OutboxMessage> consumeTenant(UUID tenantId, Duration timeout, int expectedAtLeast) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, Containers.KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        List<OutboxMessage> out = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of("billing.events.v1"));
            long deadline = System.currentTimeMillis() + timeout.toMillis();
            while (System.currentTimeMillis() < deadline
                    && (out.size() < expectedAtLeast
                            || System.currentTimeMillis() < deadline - timeout.toMillis() + 3000)) {
                for (ConsumerRecord<String, String> r : consumer.poll(Duration.ofMillis(500))) {
                    OutboxMessage m = json.readValue(r.value(), OutboxMessage.class);
                    if (m.tenantId().equals(tenantId)) {
                        out.add(m);
                    }
                }
            }
        }
        return out;
    }
}
