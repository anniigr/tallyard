/**
 * The pricing catalog: meters, customers, plans and subscriptions. Owns what can be counted and how
 * it is priced. Exposes immutable views through {@link io.tallyard.catalog.CatalogQueries} and
 * publishes {@link io.tallyard.catalog.SubscriptionActivated} when a subscription starts.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Catalog")
package io.tallyard.catalog;
