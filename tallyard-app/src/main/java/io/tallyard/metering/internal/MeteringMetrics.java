package io.tallyard.metering.internal;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.tallyard.metering.RejectionReason;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
class MeteringMetrics {

    private final Counter stored;
    private final Counter duplicates;
    private final Counter late;
    private final Map<RejectionReason, Counter> rejected = new EnumMap<>(RejectionReason.class);
    private final Timer batchTimer;
    private final Counter batches;

    MeteringMetrics(MeterRegistry registry) {
        stored = Counter.builder("tallyard.metering.events")
                .tag("outcome", "stored")
                .description("Events written to the log for the first time")
                .register(registry);
        duplicates = Counter.builder("tallyard.metering.events")
                .tag("outcome", "duplicate")
                .description("Events discarded because their id was already in the log")
                .register(registry);
        late = Counter.builder("tallyard.metering.events")
                .tag("outcome", "late")
                .description("Events applied as adjustments because their period was already rated")
                .register(registry);
        for (RejectionReason reason : RejectionReason.values()) {
            rejected.put(
                    reason,
                    Counter.builder("tallyard.metering.rejected")
                            .tag("reason", reason.name())
                            .register(registry));
        }
        batchTimer = Timer.builder("tallyard.metering.batch")
                .description("Transactional processing time of one consumed batch")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
        batches = Counter.builder("tallyard.metering.batches").register(registry);
    }

    void record(MeteringService.BatchResult result, long nanos) {
        stored.increment(result.stored());
        duplicates.increment(result.duplicates());
        late.increment(result.late());
        result.rejected().forEach((reason, n) -> rejected.get(reason).increment(n));
        batchTimer.record(nanos, java.util.concurrent.TimeUnit.NANOSECONDS);
        batches.increment();
    }

    void rejected(RejectionReason reason) {
        rejected.get(reason).increment();
    }
}
