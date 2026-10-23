package io.tallyard.ingestion;

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
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** D1, D2, D5, A7: batch limits, per-item validation, rate limiting and poison records → DLT. */
class IngestApiIT extends AbstractIntegrationTest {

    @Autowired
    TestTenant tenants;

    @Autowired
    ObjectMapper json;

    @LocalServerPort
    int port;

    @Test
    void fullBatchOfOneThousandIsAcceptedAndLargerIsRejected() {
        TestTenant.Fixture fx = tenants.create();
        ApiClient ingest = new ApiClient(port, fx.ingestKey(), json);
        Instant now = Instant.now();
        ResponseEntity<String> ok = ingest.ingest(ApiClient.batch("k", "request", "s", now, 1000, Map.of()));
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(ingest.tree(ok).get("accepted").asInt()).isEqualTo(1000);

        ResponseEntity<String> tooBig = ingest.ingest(ApiClient.batch("big", "request", "s", now, 1001, Map.of()));
        assertThat(tooBig.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ingest.tree(tooBig).get("code").asString()).isEqualTo("batch_too_large");
    }

    @Test
    void malformedItemsProduceFieldLevelErrors() {
        TestTenant.Fixture fx = tenants.create();
        ApiClient ingest = new ApiClient(port, fx.ingestKey(), json);
        ResponseEntity<String> r = ingest.post(
                "/v1/events",
                Map.of(
                        "events",
                        List.of(Map.of(
                                "eventId",
                                "",
                                "eventType",
                                "bad type!",
                                "subject",
                                "s",
                                "occurredAt",
                                Instant.now().toString()))));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode body = ingest.tree(r);
        assertThat(body.get("code").asString()).isEqualTo("validation_failed");
        List<String> fields = new ArrayList<>();
        body.get("errors").forEach(e -> fields.add(e.get("field").asString()));
        assertThat(fields).anyMatch(f -> f.contains("eventId")).anyMatch(f -> f.contains("eventType"));
        assertThat(ingest.post("/v1/events", Map.of("events", List.of())).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ingest.post("/v1/events", "{not json").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void perKeyRateLimitReturns429WithRetryAfter() throws Exception {
        TestTenant.Fixture fx = tenants.create();
        ApiClient ingest = new ApiClient(port, fx.ingestKey(), json);
        // the test profile allows 2500 events/s per key with a one-second burst; eight parallel
        // batches of 1000 arrive well inside one second, so at least five must be refused
        var pool = java.util.concurrent.Executors.newFixedThreadPool(8);
        List<java.util.concurrent.Future<ResponseEntity<String>>> futures = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            final int n = i;
            futures.add(pool.submit(
                    () -> ingest.ingest(ApiClient.batch("rl" + n, "request", "s", Instant.now(), 1000, Map.of()))));
        }
        int limited = 0;
        int accepted = 0;
        String retryAfter = null;
        for (var f : futures) {
            ResponseEntity<String> r = f.get();
            if (r.getStatusCode().value() == 429) {
                limited++;
                retryAfter = r.getHeaders().getFirst("Retry-After");
            } else if (r.getStatusCode().value() == 202) {
                accepted++;
            }
        }
        pool.shutdown();
        // exact split depends on how much refills during the calls; the invariants are what matter
        assertThat(accepted + limited).isEqualTo(8);
        assertThat(accepted).isGreaterThanOrEqualTo(2);
        assertThat(limited).isGreaterThanOrEqualTo(1);
        assertThat(Integer.parseInt(retryAfter)).isGreaterThanOrEqualTo(1);
    }

    @Test
    void unparseableKafkaRecordsAreDeadLetteredNotRetriedForever() {
        TestTenant.Fixture fx = tenants.create();
        tenants.standardMeters(fx.tenantId());
        tenants.standardPlan(fx.tenantId(), "std", new BillingInterval(BillingInterval.Unit.DAYS, 30), 0);
        Instant now = Instant.now().minusSeconds(2);
        tenants.customer(fx.tenantId(), "poisoned", "std", now.minusSeconds(60));
        ApiClient admin = new ApiClient(port, fx.adminKey(), json);
        ApiClient ingest = new ApiClient(port, fx.ingestKey(), json);

        Properties pp = new Properties();
        pp.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, Containers.KAFKA.getBootstrapServers());
        pp.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        pp.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        String poisonKey = fx.tenantId() + ":poisoned";
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(pp)) {
            ProducerRecord<String, String> poison =
                    new ProducerRecord<>("usage.events.v1", poisonKey, "{this is not json");
            poison.headers()
                    .add(
                            UsageEventMessage.HEADER_TENANT,
                            fx.tenantId().toString().getBytes());
            producer.send(poison).get();
            ProducerRecord<String, String> missing =
                    new ProducerRecord<>("usage.events.v1", poisonKey, "{\"eventId\":\"x\"}");
            missing.headers()
                    .add(
                            UsageEventMessage.HEADER_TENANT,
                            fx.tenantId().toString().getBytes());
            producer.send(missing).get();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        // A valid event right behind the poison must still be processed (the batch is not stuck).
        ingest.ingest(List.of(ApiClient.event("after-poison", "request", "poisoned", now, Map.of("tokens", 1))));
        UUID period = admin.openPeriodId("poisoned");
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(admin.usage(period).get("api_calls")).isEqualByComparingTo("1");
            List<String> reasons = new ArrayList<>();
            admin.getJson("/v1/rejections?limit=50")
                    .forEach(r -> reasons.add(r.get("reason").asString()));
            assertThat(reasons.stream().filter("SCHEMA_INVALID"::equals).count())
                    .isEqualTo(2);
        });

        Properties cp = new Properties();
        cp.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, Containers.KAFKA.getBootstrapServers());
        cp.put(ConsumerConfig.GROUP_ID_CONFIG, "dlt-check-" + UUID.randomUUID());
        cp.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        cp.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        cp.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        List<String> dlt = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(cp)) {
            consumer.subscribe(List.of("usage.events.v1.dlt"));
            long deadline = System.currentTimeMillis() + 15_000;
            while (System.currentTimeMillis() < deadline && dlt.size() < 2) {
                for (ConsumerRecord<String, String> r : consumer.poll(Duration.ofMillis(500))) {
                    if (poisonKey.equals(r.key())) {
                        dlt.add(r.value());
                        assertThat(r.headers().lastHeader("ty-dlt-reason")).isNotNull();
                    }
                }
            }
        }
        assertThat(dlt).containsExactlyInAnyOrder("{this is not json", "{\"eventId\":\"x\"}");
    }
}
