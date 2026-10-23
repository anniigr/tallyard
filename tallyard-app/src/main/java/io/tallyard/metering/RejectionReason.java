package io.tallyard.metering;

/** Why an event was not counted. Stored in {@code rejected_event.reason} and exposed as a metric tag. */
public enum RejectionReason {
    /** Payload could not be parsed or violated the message schema; also sent to the dead-letter topic. */
    SCHEMA_INVALID,
    /** No meter listens to this event type. */
    NO_MATCHING_METER,
    /** The subject is not a known customer of the tenant. */
    UNKNOWN_SUBJECT,
    /** The customer had no active subscription at the event time. */
    NO_ACTIVE_SUBSCRIPTION,
    /** Event is older than the meter's acceptance window. */
    OUTSIDE_ACCEPTANCE_WINDOW,
    /** The meter's value property is missing or not a plain decimal number. */
    INVALID_VALUE,
    /** No billing period covers the event time (should not happen while a subscription is active). */
    NO_PERIOD,
    /** Late event, but the subscription has no open period to carry the adjustment. */
    NO_OPEN_PERIOD
}
