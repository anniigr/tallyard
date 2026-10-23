/**
 * Prepaid credit grants and the immutable consumption ledger. Enforces the invariant
 * {@code amount_consumed <= amount_granted} with row locks and a database check constraint.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Credits")
package io.tallyard.credits;
