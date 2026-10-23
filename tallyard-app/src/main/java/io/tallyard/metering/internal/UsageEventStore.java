package io.tallyard.metering.internal;

import io.tallyard.billing.UsageSource;
import io.tallyard.catalog.MeterView;
import io.tallyard.ingestion.UsageEventMessage;
import io.tallyard.metering.RejectedEventView;
import io.tallyard.metering.RejectionReason;
import io.tallyard.shared.id.Ids;
import io.tallyard.shared.json.Json;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Plain JDBC for the hot path. JPA is the right tool for the catalog and the period state machine;
 * for thousands of inserts and upserts per second it would only add dirty checking and a first-level
 * cache nobody reads. Every statement here is set-oriented and batched.
 */
@Repository
class UsageEventStore {

    private static final int INSERT_CHUNK = 250;

    private final JdbcTemplate jdbc;
    private final Json json;
    private final Clock clock;

    UsageEventStore(JdbcTemplate jdbc, Json json, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    record Inserted(UUID id, UUID tenantId, String eventId) {}

    /**
     * Inserts events, skipping ids already present, and returns only the rows that were actually
     * inserted. This single statement is the deduplication mechanism: a redelivered or duplicated
     * event conflicts on {@code (tenant_id, event_id)} and is silently dropped.
     */
    Map<String, UUID> insertNew(Collection<UsageEventMessage> messages) {
        Map<String, UUID> inserted = new HashMap<>();
        List<UsageEventMessage> list = new ArrayList<>(messages);
        for (int from = 0; from < list.size(); from += INSERT_CHUNK) {
            List<UsageEventMessage> chunk = list.subList(from, Math.min(list.size(), from + INSERT_CHUNK));
            StringBuilder sql = new StringBuilder(
                    "INSERT INTO usage_event (id, tenant_id, event_id, event_type, subject, occurred_at, ingested_at, properties) VALUES ");
            List<Object> args = new ArrayList<>(chunk.size() * 8);
            for (int i = 0; i < chunk.size(); i++) {
                UsageEventMessage m = chunk.get(i);
                sql.append(i == 0 ? "" : ",").append("(?,?,?,?,?,?,?,?::jsonb)");
                args.add(Ids.newId(clock));
                args.add(m.tenantId());
                args.add(m.eventId());
                args.add(m.eventType());
                args.add(m.subject());
                args.add(Timestamp.from(m.occurredAt()));
                args.add(Timestamp.from(m.ingestedAt()));
                args.add(json.write(m.properties()));
            }
            sql.append(" ON CONFLICT (tenant_id, event_id) DO NOTHING RETURNING id, tenant_id, event_id");
            jdbc.query(
                    sql.toString(),
                    rs -> {
                        inserted.put(
                                key(rs.getObject("tenant_id", UUID.class), rs.getString("event_id")),
                                rs.getObject("id", UUID.class));
                    },
                    args.toArray());
        }
        return inserted;
    }

    static String key(UUID tenantId, String eventId) {
        return tenantId + "|" + eventId;
    }

    record BucketDelta(
            UUID tenantId,
            UUID periodId,
            UUID meterId,
            UUID customerId,
            Instant bucketStart,
            BigDecimal sum,
            long count,
            BigDecimal min,
            BigDecimal max,
            BigDecimal last,
            Instant lastOccurredAt) {}

    void upsertBuckets(List<BucketDelta> deltas) {
        if (deltas.isEmpty()) {
            return;
        }
        String sql = """
                INSERT INTO meter_bucket (tenant_id, period_id, meter_id, customer_id, bucket_start,
                                          agg_sum, agg_count, agg_min, agg_max, agg_last, last_occurred_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT (period_id, meter_id, customer_id, bucket_start) DO UPDATE SET
                    agg_sum          = meter_bucket.agg_sum + EXCLUDED.agg_sum,
                    agg_count        = meter_bucket.agg_count + EXCLUDED.agg_count,
                    agg_min          = LEAST(meter_bucket.agg_min, EXCLUDED.agg_min),
                    agg_max          = GREATEST(meter_bucket.agg_max, EXCLUDED.agg_max),
                    agg_last         = CASE WHEN meter_bucket.last_occurred_at IS NULL
                                              OR EXCLUDED.last_occurred_at >= meter_bucket.last_occurred_at
                                            THEN EXCLUDED.agg_last ELSE meter_bucket.agg_last END,
                    last_occurred_at = GREATEST(meter_bucket.last_occurred_at, EXCLUDED.last_occurred_at)
                """;
        jdbc.batchUpdate(sql, deltas, deltas.size(), (ps, d) -> {
            ps.setObject(1, d.tenantId());
            ps.setObject(2, d.periodId());
            ps.setObject(3, d.meterId());
            ps.setObject(4, d.customerId());
            ps.setTimestamp(5, Timestamp.from(d.bucketStart()));
            ps.setBigDecimal(6, d.sum());
            ps.setLong(7, d.count());
            ps.setBigDecimal(8, d.min());
            ps.setBigDecimal(9, d.max());
            ps.setBigDecimal(10, d.last());
            if (d.lastOccurredAt() == null) {
                ps.setNull(11, Types.TIMESTAMP_WITH_TIMEZONE);
            } else {
                ps.setTimestamp(11, Timestamp.from(d.lastOccurredAt()));
            }
        });
    }

    record Adjustment(
            UUID tenantId,
            UUID subscriptionId,
            UUID appliedPeriodId,
            UUID originalPeriodId,
            UUID meterId,
            UUID usageEventId,
            BigDecimal value,
            Instant occurredAt) {}

    void insertAdjustments(List<Adjustment> adjustments) {
        if (adjustments.isEmpty()) {
            return;
        }
        String sql = """
                INSERT INTO usage_adjustment (id, tenant_id, subscription_id, applied_period_id, original_period_id,
                                              meter_id, usage_event_id, value, occurred_at, created_at)
                VALUES (?,?,?,?,?,?,?,?,?,?) ON CONFLICT (usage_event_id, meter_id) DO NOTHING
                """;
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.batchUpdate(sql, adjustments, adjustments.size(), (ps, a) -> {
            ps.setObject(1, Ids.newId(clock));
            ps.setObject(2, a.tenantId());
            ps.setObject(3, a.subscriptionId());
            ps.setObject(4, a.appliedPeriodId());
            ps.setObject(5, a.originalPeriodId());
            ps.setObject(6, a.meterId());
            ps.setObject(7, a.usageEventId());
            ps.setBigDecimal(8, a.value());
            ps.setTimestamp(9, Timestamp.from(a.occurredAt()));
            ps.setTimestamp(10, now);
        });
    }

    record Rejection(
            UUID tenantId,
            String eventId,
            String eventType,
            String subject,
            Instant occurredAt,
            RejectionReason reason,
            String detail,
            String payloadJson) {}

    void insertRejections(List<Rejection> rejections) {
        if (rejections.isEmpty()) {
            return;
        }
        String sql = """
                INSERT INTO rejected_event (id, tenant_id, event_id, event_type, subject, occurred_at, reason, detail, payload, created_at)
                VALUES (?,?,?,?,?,?,?,?,?::jsonb,?)
                """;
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.batchUpdate(sql, rejections, rejections.size(), (ps, r) -> {
            ps.setObject(1, Ids.newId(clock));
            ps.setObject(2, r.tenantId());
            ps.setString(3, r.eventId());
            ps.setString(4, r.eventType());
            ps.setString(5, r.subject());
            if (r.occurredAt() == null) {
                ps.setNull(6, Types.TIMESTAMP_WITH_TIMEZONE);
            } else {
                ps.setTimestamp(6, Timestamp.from(r.occurredAt()));
            }
            ps.setString(7, r.reason().name());
            ps.setString(8, r.detail());
            ps.setString(9, r.payloadJson());
            ps.setTimestamp(10, now);
        });
    }

    // ------------------------------------------------------------------ reads

    record BucketTotals(BigDecimal sum, long count, BigDecimal min, BigDecimal max, BigDecimal last) {}

    BucketTotals bucketTotals(UUID periodId, UUID meterId, UUID customerId) {
        return jdbc.queryForObject(
                """
                SELECT coalesce(sum(agg_sum), 0) AS s, coalesce(sum(agg_count), 0) AS c, min(agg_min) AS mn, max(agg_max) AS mx,
                       (array_agg(agg_last ORDER BY last_occurred_at DESC NULLS LAST))[1] AS lst
                FROM meter_bucket WHERE period_id = ? AND meter_id = ? AND customer_id = ?
                """,
                (rs, i) -> new BucketTotals(
                        rs.getBigDecimal("s"),
                        rs.getLong("c"),
                        rs.getBigDecimal("mn"),
                        rs.getBigDecimal("mx"),
                        rs.getBigDecimal("lst")),
                periodId,
                meterId,
                customerId);
    }

    /**
     * Recomputes one meter's quantity straight from the immutable log, applying the same filters the
     * consumer applied when it counted: acceptance window and the plain-decimal grammar.
     */
    BigDecimal quantityFromLog(UsageSource.Window w, MeterView meter, Instant asOf) {
        String base = """
                FROM usage_event
                WHERE tenant_id = ? AND subject = ? AND event_type = ?
                  AND occurred_at >= ? AND occurred_at < ? AND ingested_at < ?
                  AND ingested_at - occurred_at <= make_interval(secs => ?)
                """;
        List<Object> args = new ArrayList<>(List.of(
                w.tenantId(),
                w.subject(),
                meter.eventType(),
                Timestamp.from(w.start()),
                Timestamp.from(w.end()),
                Timestamp.from(asOf),
                (double) meter.acceptanceWindow().toSeconds()));
        String numericFilter = " AND (properties->>?) ~ '^-?[0-9]{1,19}(\\.[0-9]{1,6})?$'";
        String select;
        switch (meter.aggregation()) {
            case COUNT -> select = "SELECT count(*) ";
            case SUM -> {
                select = "SELECT coalesce(sum((properties->>?)::numeric), 0) ";
                args.add(0, meter.valueProperty());
                base += numericFilter;
                args.add(meter.valueProperty());
            }
            case MIN -> {
                select = "SELECT min((properties->>?)::numeric) ";
                args.add(0, meter.valueProperty());
                base += numericFilter;
                args.add(meter.valueProperty());
            }
            case MAX -> {
                select = "SELECT max((properties->>?)::numeric) ";
                args.add(0, meter.valueProperty());
                base += numericFilter;
                args.add(meter.valueProperty());
            }
            case LAST -> {
                select =
                        "SELECT (array_agg((properties->>?)::numeric ORDER BY occurred_at DESC, ingested_at DESC))[1] ";
                args.add(0, meter.valueProperty());
                base += numericFilter;
                args.add(meter.valueProperty());
            }
            case UNIQUE_COUNT -> {
                select = "SELECT count(DISTINCT properties->>?) ";
                args.add(0, meter.valueProperty());
                // jsonb_exists(...) rather than the ? operator, which JDBC would read as a bind marker.
                base += " AND jsonb_exists(properties, ?)";
                args.add(meter.valueProperty());
            }
            default -> throw new IllegalStateException("Unknown aggregation " + meter.aggregation());
        }
        BigDecimal result = jdbc.queryForObject(select + base, BigDecimal.class, args.toArray());
        return result == null ? BigDecimal.ZERO : result;
    }

    long eventCountFromBuckets(UUID periodId, UUID meterId, UUID customerId) {
        Long n = jdbc.queryForObject(
                "SELECT coalesce(sum(agg_count), 0) FROM meter_bucket WHERE period_id = ? AND meter_id = ? AND customer_id = ?",
                Long.class,
                periodId,
                meterId,
                customerId);
        return n == null ? 0 : n;
    }

    List<UsageSource.AdjustmentView> adjustments(UUID periodId) {
        return jdbc.query(
                """
                SELECT a.id, a.meter_id, a.original_period_id, e.event_id, a.value, a.occurred_at
                FROM usage_adjustment a JOIN usage_event e ON e.id = a.usage_event_id
                WHERE a.applied_period_id = ? ORDER BY a.occurred_at
                """,
                (rs, i) -> new UsageSource.AdjustmentView(
                        rs.getObject("id", UUID.class),
                        rs.getObject("meter_id", UUID.class),
                        rs.getObject("original_period_id", UUID.class),
                        rs.getString("event_id"),
                        rs.getBigDecimal("value"),
                        rs.getTimestamp("occurred_at").toInstant()),
                periodId);
    }

    List<RejectedEventView> recentRejections(UUID tenantId, int limit) {
        return jdbc.query(
                """
                SELECT id, event_id, event_type, subject, occurred_at, reason, detail, created_at
                FROM rejected_event WHERE tenant_id = ? ORDER BY created_at DESC LIMIT ?
                """,
                (rs, i) -> new RejectedEventView(
                        rs.getObject("id", UUID.class),
                        rs.getString("event_id"),
                        rs.getString("event_type"),
                        rs.getString("subject"),
                        rs.getTimestamp("occurred_at") == null
                                ? null
                                : rs.getTimestamp("occurred_at").toInstant(),
                        RejectionReason.valueOf(rs.getString("reason")),
                        rs.getString("detail"),
                        rs.getTimestamp("created_at").toInstant()),
                tenantId,
                limit);
    }

    long[] stats(UUID tenantId) {
        return jdbc.queryForObject(
                """
                SELECT (SELECT count(*) FROM usage_event WHERE tenant_id = ?),
                       (SELECT count(*) FROM usage_adjustment WHERE tenant_id = ?),
                       (SELECT count(*) FROM rejected_event WHERE tenant_id = ?)
                """, (rs, i) -> new long[] {rs.getLong(1), rs.getLong(2), rs.getLong(3)}, tenantId, tenantId, tenantId);
    }
}
