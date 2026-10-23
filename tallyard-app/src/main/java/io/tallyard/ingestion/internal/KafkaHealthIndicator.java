package io.tallyard.ingestion.internal;

import java.time.Duration;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.DescribeClusterOptions;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

/**
 * Readiness contributor for the event log. A node whose broker connection is down must leave the
 * load balancer: ingestion would only answer 503. The admin client is created once and reused.
 */
@Component("kafka")
class KafkaHealthIndicator implements HealthIndicator, AutoCloseable {

    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    private final AdminClient admin;

    KafkaHealthIndicator(KafkaAdmin kafkaAdmin) {
        Properties props = new Properties();
        props.putAll(kafkaAdmin.getConfigurationProperties());
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, (int) TIMEOUT.toMillis());
        props.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, (int) TIMEOUT.toMillis());
        this.admin = AdminClient.create(props);
    }

    @Override
    public Health health() {
        try {
            DescribeClusterResult cluster =
                    admin.describeCluster(new DescribeClusterOptions().timeoutMs((int) TIMEOUT.toMillis()));
            int nodes = cluster.nodes()
                    .get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
                    .size();
            String clusterId = cluster.clusterId().get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            return Health.up()
                    .withDetails(Map.of("clusterId", clusterId, "nodes", nodes))
                    .build();
        } catch (ExecutionException | TimeoutException e) {
            return Health.down()
                    .withDetail("error", String.valueOf(e.getCause() == null ? e : e.getCause()))
                    .build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Health.down(e).build();
        }
    }

    @Override
    public void close() {
        admin.close(Duration.ofSeconds(2));
    }
}
