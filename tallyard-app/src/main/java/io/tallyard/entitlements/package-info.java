/**
 * Real-time entitlement decisions ("may this subject consume N more units of this meter?"). Reads
 * live usage and plan limits, estimates remaining prepaid credit by rating the open period, and
 * caches snapshots for a bounded, documented staleness ({@code tallyard.entitlements.cache-ttl}).
 */
@org.springframework.modulith.ApplicationModule(displayName = "Entitlements")
package io.tallyard.entitlements;
