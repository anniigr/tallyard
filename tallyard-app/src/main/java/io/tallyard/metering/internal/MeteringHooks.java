package io.tallyard.metering.internal;

import org.springframework.stereotype.Component;

/**
 * Seam for failure-injection tests: runs after the batch transaction commits and before the Kafka
 * offsets are acknowledged. Throwing here simulates a worker that crashed in exactly the window
 * where at-least-once delivery produces a redelivery of already-persisted events.
 */
@Component
public class MeteringHooks {

    private volatile Runnable afterCommit = () -> {};

    public void setAfterCommit(Runnable hook) {
        this.afterCommit = hook == null ? () -> {} : hook;
    }

    public void reset() {
        this.afterCommit = () -> {};
    }

    void afterCommit() {
        afterCommit.run();
    }
}
