package io.tallyard.shared.role;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;

/** Marks beans that run only in the {@code api} (or {@code all}) role. */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@ConditionalOnExpression("'${tallyard.role:all}' == 'all' or '${tallyard.role:all}' == 'api'")
public @interface ApiRole {}
