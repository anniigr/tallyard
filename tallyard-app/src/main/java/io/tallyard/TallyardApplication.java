package io.tallyard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

/**
 * Tallyard: billing-grade usage metering, rating and entitlement engine.
 *
 * <p>The application is a modular monolith. Each top-level package under {@code io.tallyard} is a
 * Spring Modulith application module with an explicit public API in its root package and internals
 * in {@code internal}. Module boundaries are verified by {@code ModularityTest}.
 */
@SpringBootApplication
@Modulithic(sharedModules = "shared", systemName = "Tallyard")
public class TallyardApplication {

    public static void main(String[] args) {
        SpringApplication.run(TallyardApplication.class, args);
    }
}
