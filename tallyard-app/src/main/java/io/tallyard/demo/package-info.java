/**
 * Seeds a demo tenant (meters, plans, customers, credits, webhook) on startup when
 * {@code tallyard.demo.enabled=true}. Uses only public module APIs; never active in production
 * configurations.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Demo")
package io.tallyard.demo;
