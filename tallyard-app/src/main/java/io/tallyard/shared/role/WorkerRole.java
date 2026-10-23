package io.tallyard.shared.role;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;

/**
 * Marks beans that run only in the {@code worker} (or {@code all}) role: Kafka consumers, the
 * billing scheduler, the outbox publisher and webhook dispatcher. The same artifact runs as an
 * API node or a worker node depending on {@code tallyard.role}.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@ConditionalOnExpression("'${tallyard.role:all}' == 'all' or '${tallyard.role:all}' == 'worker'")
public @interface WorkerRole {}
