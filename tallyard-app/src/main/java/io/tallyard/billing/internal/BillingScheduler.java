package io.tallyard.billing.internal;

import io.tallyard.shared.role.WorkerRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drives the period lifecycle. Safe to run on several worker instances at once: each step claims
 * rows with {@code FOR UPDATE SKIP LOCKED}, and the entity version catches anything that slips
 * through. Batches are small so one slow rating does not hold many locks.
 */
@Component
@WorkerRole
class BillingScheduler {

    private static final Logger log = LoggerFactory.getLogger(BillingScheduler.class);
    private static final int BATCH = 25;

    private final BillingService billing;

    BillingScheduler(BillingService billing) {
        this.billing = billing;
    }

    @Scheduled(fixedDelayString = "${tallyard.billing.scheduler-interval}")
    public void tick() {
        run("close", () -> billing.closeDue(BATCH));
        run("rate", () -> billing.rateDue(BATCH));
        run("finalize", () -> billing.finalizeDue(BATCH));
    }

    private void run(String step, java.util.function.IntSupplier action) {
        try {
            int n = action.getAsInt();
            if (n > 0) {
                log.info("Billing scheduler: {} processed {} period(s)", step, n);
            }
        } catch (OptimisticLockingFailureException e) {
            log.info("Billing scheduler: {} lost a race; will retry next tick", step);
        } catch (RuntimeException e) {
            log.error("Billing scheduler: {} failed", step, e);
        }
    }
}
