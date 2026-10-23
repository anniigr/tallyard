package io.tallyard.support;

import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Singleton containers shared by every integration test in the JVM: starting PostgreSQL and Kafka
 * once keeps the suite fast while every test still runs against real infrastructure.
 */
public final class Containers {

    public static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
                    DockerImageName.parse("postgres:17-alpine"))
            .withDatabaseName("tallyard")
            .withUsername("tallyard")
            .withPassword("tallyard")
            .withReuse(false);

    public static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka:4.1.0"));

    static {
        POSTGRES.start();
        KAFKA.start();
    }

    private Containers() {}
}
