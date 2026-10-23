package io.tallyard.demo;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param adminKey optional fixed ADMIN+INGEST key (format {@code ty_} + 40 chars) so dashboards and simulators can use a known value
 * @param periodMinutes length of the demo plans' billing period; short so a full lifecycle fits in a demo
 * @param graceSeconds grace window after period end before rating
 * @param customers number of demo customers
 * @param webhookUrl where the demo webhook endpoint points (the built-in sink by default)
 */
@ConfigurationProperties(prefix = "tallyard.demo")
public record DemoProperties(
        boolean enabled, String adminKey, int periodMinutes, int graceSeconds, int customers, String webhookUrl) {}
