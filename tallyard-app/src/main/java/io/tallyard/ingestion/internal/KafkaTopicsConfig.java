package io.tallyard.ingestion.internal;

import io.tallyard.shared.config.TallyardProperties;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Topic definitions are code, not console clicks: created idempotently at startup by KafkaAdmin.
 * Retention on the usage topic is long because it is also a replay source when buckets must be
 * rebuilt (the database remains the system of record).
 */
@Configuration
class KafkaTopicsConfig {

    @Bean
    NewTopic usageEventsTopic(TallyardProperties props) {
        return TopicBuilder.name(props.topics().usageEvents())
                .partitions(props.topics().partitions())
                .replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, Long.toString(7L * 24 * 3600 * 1000))
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_DELETE)
                .build();
    }

    @Bean
    NewTopic usageEventsDeadLetterTopic(TallyardProperties props) {
        return TopicBuilder.name(props.topics().usageEventsDlt())
                .partitions(1)
                .replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, Long.toString(30L * 24 * 3600 * 1000))
                .build();
    }

    @Bean
    NewTopic billingEventsTopic(TallyardProperties props) {
        return TopicBuilder.name(props.topics().billingEvents())
                .partitions(props.topics().partitions())
                .replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, Long.toString(30L * 24 * 3600 * 1000))
                .build();
    }
}
