package io.tallyard.catalog;

/**
 * Published synchronously inside the transaction that creates a subscription so that the billing
 * module can open the first period atomically with it.
 */
public record SubscriptionActivated(SubscriptionView subscription, PlanView plan) {}
