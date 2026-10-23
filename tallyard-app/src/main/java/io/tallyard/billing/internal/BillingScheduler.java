package io.tallyard.billing.internal;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.tallyard.shared.role.WorkerRole;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drives the period lifecycle. Each tick claims the ids of due periods and processes every period in
 * its own transaction, so a single failing period (misconfigured plan, constraint violation) is
 * counted, logged and retried next tick without blocking the rest. Safe on several worker instances:
 * each step re-locks the row and re-checks its state.
 */
@Component
@WorkerRole
class BillingScheduler {

    private static final Logger log = LoggerFactory.getLogger(BillingScheduler.class);
    private static final int BATCH = 25;

    private final BillingService billing;
    private final Counter failures;
    private final AtomicLong overdue = new AtomicLong();

    BillingScheduler(BillingService billing, MeterRegistry registry) {
        this.billing = billing;
        this.failures = Counter.builder("tallyard.billing.failures")
                .description("Period lifecycle steps that threw and will be retried")
                .register(registry);
        Gauge.builder("tallyard.billing.overdue_periods", overdue, AtomicLong::get)
                .description("Periods still due for closing/rating/finalizing after the last tick")
                .register(registry);
    }

    @Scheduled(fixedDelayString = "${tallyard.billing.scheduler-interval}")
    public void tick() {
        int left = 0;
        left += run("close", billing.dueForClosing(BATCH), billing::closeOne);
        left += run("rate", billing.dueForRating(BATCH), billing::rateOne);
        left += run("finalize", billing.dueForFinalizing(BATCH), billing::finalizeOne);
        overdue.set(left);
    }

    /** @return number of periods that could not be processed this tick */
    private int run(String step, List<UUID> ids, Predicate<UUID> action) {
        int done = 0;
        int failed = 0;
        for (UUID id : ids) {
            try {
                if (action.test(id)) {
                    done++;
                }
            } catch (OptimisticLockingFailureException e) {
                log.info("Billing scheduler: {} {} lost a race; will retry next tick", step, id);
            } catch (RuntimeException e) {
                failed++;
                failures.increment();
                log.error("Billing scheduler: {} {} failed: {}", step, id, e.toString());
            }
        }
        if (done > 0) {
            log.info("Billing scheduler: {} processed {} period(s)", step, done);
        }
        return failed;
    }
}
