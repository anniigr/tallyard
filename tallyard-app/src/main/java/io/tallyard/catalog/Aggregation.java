package io.tallyard.catalog;

/** How events are folded into a meter quantity. */
public enum Aggregation {
    SUM,
    COUNT,
    MIN,
    MAX,
    LAST,
    /** Number of distinct values of {@code valueProperty} within the period; computed from the event log. */
    UNIQUE_COUNT;

    public boolean requiresValue() {
        return this != COUNT;
    }

    public boolean isNumeric() {
        return this != UNIQUE_COUNT;
    }
}
