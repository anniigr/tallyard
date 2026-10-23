package io.tallyard.billing;

import java.util.Set;

/**
 * Lifecycle of a billing period. Transitions are irreversible: OPEN → CLOSING → RATED → FINALIZED.
 * Usage is accepted into a period while it is OPEN or CLOSING; afterwards late events become
 * adjustments on the subscription's current open period.
 */
public enum PeriodState {
    OPEN,
    CLOSING,
    RATED,
    FINALIZED;

    public boolean acceptsUsage() {
        return this == OPEN || this == CLOSING;
    }

    public boolean canTransitionTo(PeriodState next) {
        return switch (this) {
            case OPEN -> next == CLOSING;
            case CLOSING -> next == RATED;
            case RATED -> next == FINALIZED;
            case FINALIZED -> false;
        };
    }

    public static Set<PeriodState> settled() {
        return Set.of(RATED, FINALIZED);
    }
}
