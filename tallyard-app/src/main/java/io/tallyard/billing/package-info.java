/**
 * Billing periods, rating and invoice drafts. Owns the period lifecycle
 * (OPEN → CLOSING → RATED → FINALIZED), the deterministic rating engine and replay verification.
 * Reads usage through the {@link io.tallyard.billing.UsageSource} SPI, which the metering module
 * implements (dependency inversion keeps billing independent of metering internals).
 */
@org.springframework.modulith.ApplicationModule(displayName = "Billing")
package io.tallyard.billing;
