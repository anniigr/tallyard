package io.tallyard.metering.internal;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Whole-batch failures (database unavailable, lock timeouts) are retried indefinitely with
 * exponential backoff capped at 30 s. There is deliberately no dead-lettering of entire batches:
 * that would turn an outage into silent data loss. Per-record poison is handled inside the consumer.
 */
@Configuration
class KafkaConsumerConfig {

    @Bean
    CommonErrorHandler meteringErrorHandler() {
        ExponentialBackOff backOff = new ExponentialBackOff(500, 2.0);
        backOff.setMaxInterval(30_000);
        DefaultErrorHandler handler = new DefaultErrorHandler(backOff);
        handler.setAckAfterHandle(false);
        return handler;
    }
}
