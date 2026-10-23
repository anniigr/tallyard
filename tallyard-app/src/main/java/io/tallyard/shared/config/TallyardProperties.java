package io.tallyard.shared.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed view of the {@code tallyard.*} configuration namespace. */
@ConfigurationProperties(prefix = "tallyard")
public record TallyardProperties(
        String role,
        String bootstrapToken,
        String encryptionKey,
        Topics topics,
        Ingestion ingestion,
        Metering metering,
        Billing billing,
        Entitlements entitlements,
        Outbox outbox,
        Webhooks webhooks) {

    public record Topics(String usageEvents, String usageEventsDlt, String billingEvents, int partitions) {}

    public record Ingestion(
            int maxBatchSize, Duration maxFutureSkew, int rateLimitPerKeyPerSecond, Duration kafkaSendTimeout) {}

    public record Metering(String consumerGroup) {}

    public record Billing(Duration schedulerInterval, Duration autoFinalizeAfter) {}

    public record Entitlements(Duration cacheTtl, long cacheMaxSize) {}

    public record Outbox(Duration pollInterval, int batchSize, int maxAttempts) {}

    public record Webhooks(
            Duration pollInterval, int batchSize, int maxAttempts, Duration initialBackoff, Duration requestTimeout) {}
}
