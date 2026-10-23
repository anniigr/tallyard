package io.tallyard.simulator;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Load, chaos and truth-check generator for Tallyard.
 *
 * <p>Streams realistic usage for N customers at a target rate, deliberately injecting duplicates
 * (client retries), late events (offline SDK flushes) and invalid payloads, while keeping an exact
 * local tally of what <em>should</em> be counted. At the end it reads back live usage per customer
 * and reports whether Tallyard's totals equal the expected totals. Run it while killing the worker
 * to prove effectively-once accounting under crashes.
 *
 * <pre>
 * java -jar tallyard-simulator.jar --url http://localhost:8080 --key ty_... \
 *      --customers 20 --rate 500 --duration 60 --duplicates 0.05 --late 0.02 --invalid 0.01
 * </pre>
 */
public final class Simulator {

    record Options(
            String url,
            String key,
            int customers,
            int rate,
            int durationSeconds,
            double duplicates,
            double late,
            double invalid,
            int batchSize,
            int threads,
            long seed,
            boolean verify,
            String customerPrefix) {

        static Options parse(String[] args) {
            Map<String, String> m = new java.util.HashMap<>();
            for (int i = 0; i < args.length; i++) {
                if (args[i].startsWith("--")) {
                    String k = args[i].substring(2);
                    String v = (i + 1 < args.length && !args[i + 1].startsWith("--")) ? args[++i] : "true";
                    m.put(k, v);
                }
            }
            return new Options(
                    m.getOrDefault("url", "http://localhost:8080"),
                    m.getOrDefault("key", System.getenv().getOrDefault("TALLYARD_API_KEY", "")),
                    Integer.parseInt(m.getOrDefault("customers", "20")),
                    Integer.parseInt(m.getOrDefault("rate", "500")),
                    Integer.parseInt(m.getOrDefault("duration", "60")),
                    Double.parseDouble(m.getOrDefault("duplicates", "0.05")),
                    Double.parseDouble(m.getOrDefault("late", "0.02")),
                    Double.parseDouble(m.getOrDefault("invalid", "0.01")),
                    Integer.parseInt(m.getOrDefault("batch", "100")),
                    Integer.parseInt(m.getOrDefault("threads", "4")),
                    Long.parseLong(m.getOrDefault("seed", Long.toString(System.nanoTime()))),
                    !m.getOrDefault("verify", "true").equals("false"),
                    m.getOrDefault("customer-prefix", "cust-"));
        }
    }

    /** What we expect Tallyard to count for one customer in the current period. */
    static final class Expected {
        final LongAdder apiCalls = new LongAdder();
        final LongAdder tokens = new LongAdder();
        final AtomicLong maxStorageGb = new AtomicLong(0);
        final java.util.Set<String> devices = ConcurrentHashMap.newKeySet();
        final LongAdder lateEvents = new LongAdder();
    }

    static final class Stats {
        final LongAdder sent = new LongAdder();
        final LongAdder duplicatesSent = new LongAdder();
        final LongAdder invalidSent = new LongAdder();
        final LongAdder lateSent = new LongAdder();
        final LongAdder accepted = new LongAdder();
        final LongAdder invalidReported = new LongAdder();
        final LongAdder http503 = new LongAdder();
        final LongAdder http429 = new LongAdder();
        final LongAdder httpOther = new LongAdder();
        final LongAdder retries = new LongAdder();
        final List<Long> latenciesMicros = java.util.Collections.synchronizedList(new ArrayList<>());
    }

    private final Options opts;
    private final HttpClient http =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper json = new ObjectMapper();
    private final Map<String, Expected> expected = new ConcurrentHashMap<>();
    private final Stats stats = new Stats();
    private final AtomicLong eventSeq = new AtomicLong();
    private final Instant runId = Instant.now();

    Simulator(Options opts) {
        this.opts = opts;
    }

    public static void main(String[] args) throws Exception {
        Options opts = Options.parse(args);
        if (opts.key().isBlank()) {
            System.err.println("Missing --key (or TALLYARD_API_KEY)");
            System.exit(2);
        }
        new Simulator(opts).run();
    }

    void run() throws Exception {
        System.out.printf(
                Locale.ROOT,
                "Tallyard simulator: %d customers, %d events/s for %ds, duplicates=%.0f%% late=%.0f%% invalid=%.0f%% seed=%d%n",
                opts.customers(),
                opts.rate(),
                opts.durationSeconds(),
                opts.duplicates() * 100,
                opts.late() * 100,
                opts.invalid() * 100,
                opts.seed());
        Map<String, Instant> periodStarts = opts.verify() ? loadOpenPeriodStarts() : Map.of();

        ExecutorService pool = Executors.newFixedThreadPool(opts.threads());
        Random random = new Random(opts.seed());
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(opts.durationSeconds());
        long nanosPerBatch = TimeUnit.SECONDS.toNanos(1) * opts.batchSize() / opts.rate();
        long next = System.nanoTime();
        long lastReport = System.nanoTime();
        while (System.nanoTime() < end) {
            List<Map<String, Object>> batch = buildBatch(random, periodStarts);
            pool.submit(() -> send(batch));
            next += nanosPerBatch;
            long sleep = next - System.nanoTime();
            if (sleep > 0) {
                TimeUnit.NANOSECONDS.sleep(sleep);
            }
            if (System.nanoTime() - lastReport > TimeUnit.SECONDS.toNanos(5)) {
                lastReport = System.nanoTime();
                System.out.printf(
                        Locale.ROOT,
                        "  sent=%d accepted=%d dup=%d late=%d invalid=%d 503=%d 429=%d p99=%.1fms%n",
                        stats.sent.sum(),
                        stats.accepted.sum(),
                        stats.duplicatesSent.sum(),
                        stats.lateSent.sum(),
                        stats.invalidSent.sum(),
                        stats.http503.sum(),
                        stats.http429.sum(),
                        percentile(0.99) / 1000.0);
            }
        }
        pool.shutdown();
        pool.awaitTermination(2, TimeUnit.MINUTES);
        report();
        if (opts.verify()) {
            verify();
        }
    }

    private List<Map<String, Object>> buildBatch(Random random, Map<String, Instant> periodStarts) {
        List<Map<String, Object>> batch = new ArrayList<>(opts.batchSize() + 8);
        Instant now = Instant.now();
        for (int i = 0; i < opts.batchSize(); i++) {
            String customer = opts.customerPrefix() + String.format("%03d", 1 + random.nextInt(opts.customers()));
            Expected exp = expected.computeIfAbsent(customer, k -> new Expected());
            double roll = random.nextDouble();
            String eventId = "sim-" + runId.getEpochSecond() + "-" + eventSeq.incrementAndGet();
            Map<String, Object> event;
            if (roll < opts.invalid()) {
                // Semantically invalid: tokens is not a number → INVALID_VALUE for the tokens meter,
                // but api_calls (COUNT) still counts it. Keep the tally honest about that.
                event = event(eventId, "request", customer, now, Map.of("tokens", "not-a-number"));
                exp.apiCalls.increment();
                stats.invalidSent.increment();
            } else if (roll < opts.invalid() + opts.late()) {
                // Late: occurred before the current period started → adjustment, not counted in live usage.
                Instant start = periodStarts.get(customer);
                Instant occurred =
                        start == null ? now.minus(Duration.ofHours(2)) : start.minusSeconds(30 + random.nextInt(600));
                event = event(eventId, "request", customer, occurred, Map.of("tokens", 100));
                exp.lateEvents.increment();
                stats.lateSent.increment();
            } else {
                int kind = random.nextInt(10);
                if (kind < 7) {
                    int tokens = 50 + random.nextInt(2000);
                    event = event(eventId, "request", customer, now, Map.of("tokens", tokens));
                    exp.apiCalls.increment();
                    exp.tokens.add(tokens);
                } else if (kind < 9) {
                    int gb = 10 + random.nextInt(200);
                    event = event(eventId, "storage", customer, now, Map.of("gb", gb));
                    exp.maxStorageGb.accumulateAndGet(gb, Math::max);
                } else {
                    String device = "dev-" + random.nextInt(25);
                    event = event(eventId, "heartbeat", customer, now, Map.of("device_id", device));
                    exp.devices.add(device);
                }
            }
            batch.add(event);
            if (random.nextDouble() < opts.duplicates()) {
                batch.add(event); // client retry inside the same batch
                stats.duplicatesSent.increment();
            }
        }
        if (random.nextDouble() < opts.duplicates() * 2) {
            // whole-batch retry: resend the first half of this batch again
            batch.addAll(new ArrayList<>(batch.subList(0, batch.size() / 2)));
            stats.duplicatesSent.add(batch.size() / 3);
        }
        return batch;
    }

    private static Map<String, Object> event(
            String id, String type, String subject, Instant at, Map<String, Object> props) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("eventId", id);
        m.put("eventType", type);
        m.put("subject", subject);
        m.put("occurredAt", at.toString());
        m.put("properties", props);
        return m;
    }

    private void send(List<Map<String, Object>> batch) {
        String body = json.writeValueAsString(Map.of("events", batch));
        for (int attempt = 1; attempt <= 20; attempt++) {
            long started = System.nanoTime();
            try {
                HttpResponse<String> response = http.send(
                        HttpRequest.newBuilder(URI.create(opts.url() + "/v1/events"))
                                .timeout(Duration.ofSeconds(15))
                                .header("Content-Type", "application/json")
                                .header("X-Api-Key", opts.key())
                                .POST(HttpRequest.BodyPublishers.ofString(body))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
                stats.latenciesMicros.add((System.nanoTime() - started) / 1000);
                int code = response.statusCode();
                if (code == 202) {
                    JsonNode r = json.readTree(response.body());
                    stats.sent.add(batch.size());
                    stats.accepted.add(r.get("accepted").asInt());
                    stats.invalidReported.add(r.get("invalid").asInt());
                    return;
                }
                if (code == 503 || code == 429) {
                    (code == 503 ? stats.http503 : stats.http429).increment();
                    stats.retries.increment();
                    String retryAfter =
                            response.headers().firstValue("Retry-After").orElse("1");
                    Thread.sleep(Math.min(5000, Long.parseLong(retryAfter) * 1000L));
                    continue; // same eventIds → safe
                }
                stats.httpOther.increment();
                System.err.println("Unexpected HTTP " + code + ": " + response.body());
                return;
            } catch (IOException e) {
                stats.retries.increment();
                try {
                    Thread.sleep(Math.min(5000, 250L * attempt));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        System.err.println("Giving up on a batch after 20 attempts");
    }

    private double percentile(double p) {
        List<Long> copy;
        synchronized (stats.latenciesMicros) {
            copy = new ArrayList<>(stats.latenciesMicros);
        }
        if (copy.isEmpty()) {
            return 0;
        }
        copy.sort(null);
        return copy.get((int) Math.min(copy.size() - 1, Math.floor(p * copy.size())));
    }

    private void report() {
        System.out.println();
        System.out.println("=== Send summary ===");
        System.out.printf(Locale.ROOT, "events sent (incl. duplicates): %d%n", stats.sent.sum());
        System.out.printf(Locale.ROOT, "accepted by API:                %d%n", stats.accepted.sum());
        System.out.printf(Locale.ROOT, "duplicates injected:            %d%n", stats.duplicatesSent.sum());
        System.out.printf(Locale.ROOT, "late injected:                  %d%n", stats.lateSent.sum());
        System.out.printf(Locale.ROOT, "invalid injected:               %d%n", stats.invalidSent.sum());
        System.out.printf(
                Locale.ROOT,
                "503 / 429 / other:              %d / %d / %d (retries %d)%n",
                stats.http503.sum(),
                stats.http429.sum(),
                stats.httpOther.sum(),
                stats.retries.sum());
        System.out.printf(
                Locale.ROOT,
                "batch latency p50/p95/p99:      %.1f / %.1f / %.1f ms%n",
                percentile(0.5) / 1000.0,
                percentile(0.95) / 1000.0,
                percentile(0.99) / 1000.0);
    }

    private Map<String, Instant> loadOpenPeriodStarts() throws Exception {
        Map<String, Instant> starts = new java.util.HashMap<>();
        JsonNode periods = getJson("/v1/periods");
        JsonNode customers = getJson("/v1/customers");
        Map<String, String> customerIdToExternal = new java.util.HashMap<>();
        for (JsonNode c : customers) {
            customerIdToExternal.put(c.get("id").asString(), c.get("externalId").asString());
        }
        for (JsonNode p : periods) {
            if (p.get("state").asString().equals("OPEN")) {
                String ext = customerIdToExternal.get(p.get("customerId").asString());
                if (ext != null) {
                    starts.put(ext, Instant.parse(p.get("startsAt").asString()));
                }
            }
        }
        return starts;
    }

    private void verify() throws Exception {
        System.out.println();
        System.out.println("=== Truth check (waiting for the consumer to drain) ===");
        TreeMap<String, Expected> sorted = new TreeMap<>(expected);
        int mismatches = -1;
        for (int round = 0; round < 60; round++) {
            mismatches = 0;
            StringBuilder detail = new StringBuilder();
            for (Map.Entry<String, Expected> e : sorted.entrySet()) {
                Map<String, BigDecimal> usage = liveUsage(e.getKey());
                if (usage == null) {
                    mismatches++;
                    detail.append("  ").append(e.getKey()).append(": no open period\n");
                    continue;
                }
                Expected x = e.getValue();
                boolean ok = eq(usage.get("api_calls"), x.apiCalls.sum())
                        && eq(usage.get("tokens"), x.tokens.sum())
                        && (usage.get("storage_gb") == null || eq(usage.get("storage_gb"), x.maxStorageGb.get()))
                        && (usage.get("active_devices") == null || eq(usage.get("active_devices"), x.devices.size()));
                if (!ok) {
                    mismatches++;
                    detail.append(String.format(
                            "  %s: expected calls=%d tokens=%d gb=%d devices=%d, got %s%n",
                            e.getKey(),
                            x.apiCalls.sum(),
                            x.tokens.sum(),
                            x.maxStorageGb.get(),
                            x.devices.size(),
                            usage));
                }
            }
            if (mismatches == 0) {
                long late = sorted.values().stream()
                        .mapToLong(x -> x.lateEvents.sum())
                        .sum();
                System.out.printf(
                        Locale.ROOT,
                        "ALL %d CUSTOMERS MATCH: Tallyard's live totals equal the simulator's expected totals exactly.%n",
                        sorted.size());
                System.out.printf(
                        Locale.ROOT,
                        "(%d late events were sent and are expected as adjustments on the next rating, not in live usage.)%n",
                        late);
                return;
            }
            if (round % 5 == 4) {
                System.out.printf(
                        Locale.ROOT, "  %d customer(s) still differ after %ds...%n%s", mismatches, round + 1, detail);
            }
            Thread.sleep(1000);
        }
        System.out.printf(
                Locale.ROOT,
                "MISMATCH: %d customer(s) differ after waiting 60s. Expected totals != stored totals.%n",
                mismatches);
        System.exit(1);
    }

    private static boolean eq(BigDecimal actual, long expected) {
        return actual != null && actual.compareTo(BigDecimal.valueOf(expected)) == 0;
    }

    private Map<String, BigDecimal> liveUsage(String customer) throws Exception {
        JsonNode periods = getJson("/v1/periods?customer=" + customer);
        for (JsonNode p : periods) {
            if (p.get("state").asString().equals("OPEN")) {
                Map<String, BigDecimal> out = new java.util.HashMap<>();
                for (JsonNode u : getJson("/v1/periods/" + p.get("id").asString() + "/usage")) {
                    out.put(u.get("meterCode").asString(), u.get("quantity").decimalValue());
                }
                return out;
            }
        }
        return null;
    }

    private JsonNode getJson(String path) throws Exception {
        HttpResponse<String> r = http.send(
                HttpRequest.newBuilder(URI.create(opts.url() + path))
                        .header("X-Api-Key", opts.key())
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() != 200) {
            throw new IllegalStateException("GET " + path + " → HTTP " + r.statusCode() + ": " + r.body());
        }
        return json.readTree(r.body());
    }
}
