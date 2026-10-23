# Security Review

*Phase 10 deliverable, 2026-10-23. Scope: the `tallyard-app` code base, its configuration and the
local deployment. Findings are classified as implemented control, accepted limitation, or follow-up.*

## 1. Authentication and authorization

| Topic | Status | Notes |
|---|---|---|
| API-key authentication on `/v1/**` | Implemented | `ApiKeyAuthenticationFilter` accepts `X-Api-Key` or `Authorization: Bearer`; unknown/revoked keys → 401 problem+json. Stateless, no sessions, CSRF disabled (no cookies). |
| Key storage | Implemented | Only SHA-256 hex of the key and an 11-character public prefix are stored (`api_key.key_hash UNIQUE`). The secret is returned once at creation and never listed. |
| Key format and entropy | Implemented | `ty_` + 40 characters from a 62-symbol alphabet via `SecureRandom` (~238 bits). |
| Scopes | Implemented | `INGEST`, `READ`, `ADMIN` (ADMIN implies READ). Enforced per endpoint with `@PreAuthorize("@scopes.has('…')")`; verified by `SecurityIT`. |
| Tenant isolation | Implemented | Tenant id comes only from the authenticated key (`TenantContext`); every repository query filters by tenant; foreign ids resolve to 404, never 403, to avoid existence leaks. Verified by `SecurityIT.tenantsAreIsolated`. |
| Revocation | Implemented | `DELETE /v1/api-keys/{id}` sets `revoked_at`; local auth cache is evicted; across nodes residual validity ≤ 30 s (cache TTL) — documented. |
| Tenant bootstrap | Implemented | `POST /v1/admin/tenants` requires `X-Bootstrap-Token` compared in constant time; endpoint disabled when `TALLYARD_BOOTSTRAP_TOKEN` is empty. |
| Demo fixed key | Accepted limitation | `TALLYARD_DEMO_ADMIN_KEY` installs a known key for the seeded demo tenant **only when the demo is enabled**; `.env.example` documents it as local-only. Production deployments must leave `TALLYARD_DEMO_ENABLED=false`. |

## 2. Input validation

| Topic | Status | Notes |
|---|---|---|
| Request bodies | Implemented | Bean Validation on every DTO (sizes, patterns for codes/event types/currencies, numeric bounds); field-level errors in problem+json. |
| Batch limits | Implemented | ≤ 1000 events per request (`batch_too_large`), ≤ 50 properties per event, nesting depth ≤ 3, strings ≤ 1024 chars, arrays ≤ 100. |
| Time sanity | Implemented | `occurredAt` more than 5 minutes in the future is rejected per item; acceptance window per meter bounds how old an event may be. |
| Numeric values | Implemented | Only the plain-decimal grammar `-?\d{1,19}(\.\d{1,6})?` is accepted (Java and SQL agree); everything else is `INVALID_VALUE`. Prevents `NaN`/exponent surprises in money arithmetic. |
| Kafka payloads | Implemented | Our own producer writes them, but the consumer still parses defensively; unparseable records → DLT + `SCHEMA_INVALID`, never a stuck partition. |
| Webhook URLs | Implemented | Must be absolute `http(s)` with a host. **Follow-up:** no SSRF allow/deny list (private ranges, link-local, metadata endpoints) — required before multi-tenant production use. |

## 3. Injection and persistence

| Topic | Status | Notes |
|---|---|---|
| SQL injection | Implemented | All JDBC statements use bind parameters; the only string-built SQL is the `VALUES (?,?,…)` placeholder list and aggregation `SELECT` fragments chosen from an enum. JPQL queries are parameterised. |
| JSONB | Implemented | Properties are serialised by Jackson and bound as parameters with `?::jsonb`; the JSON `?` operator is avoided (`jsonb_exists`). |
| Schema generation | Implemented | Flyway-only; Hibernate validates. No `ddl-auto=update` anywhere. |
| Mass assignment | Implemented | Request DTOs are records separate from entities; ids and tenant ids are never taken from bodies. |

## 4. Secrets and sensitive data

| Topic | Status | Notes |
|---|---|---|
| Repository | Implemented | No credentials committed; `.env` git-ignored; `.env.example` has placeholders; CI runs gitleaks. |
| Webhook signing secrets | Implemented | AES-256-GCM at rest with a key from `TALLYARD_ENCRYPTION_KEY`; random 96-bit IV per record; registering an endpoint without a key fails with a clear 409. |
| Webhook signatures | Implemented | `X-Tallyard-Signature: t=<ts>,v1=<HMAC-SHA256(secret, t.body)>`; receivers can reject stale timestamps. Verified against WireMock in `WebhookDeliveryIT`. |
| Database credentials | Accepted limitation | Supplied by environment (`TALLYARD_DB_*`); the Compose default password is for local use only and documented as such. |
| Logging | Implemented | API keys are never logged (only prefix via MDC `tenant`); request bodies are not logged; `server.error.include-message=never`. Structured JSON logging available via `TALLYARD_LOG_FORMAT=ecs`. |
| Key encryption key rotation | Follow-up | No re-encryption routine for a rotated `TALLYARD_ENCRYPTION_KEY`; would need a versioned key id per row. |

## 5. Dependencies and supply chain

| Topic | Status | Notes |
|---|---|---|
| Versions | Implemented | All on current GA lines (Spring Boot 4.1.2, Spring Security 7.1, Hibernate 7.4, Kafka clients 4.2, PostgreSQL driver 42.7.14). |
| CI checks | Implemented | `dependency-review-action` on pull requests; gitleaks on every push. |
| Vulnerability scanning | Follow-up | OWASP Dependency-Check / Trivy image scan not wired (both are slow and need an NVD key); recommended as a scheduled job. |

## 6. Containers and runtime

| Topic | Status | Notes |
|---|---|---|
| Image | Implemented | Multi-stage build; `eclipse-temurin:21-jre-alpine`; non-root user `tallyard`; layered jar for cache-friendly rebuilds. |
| Ports | Implemented | Application `8080`; management `9091` on a separate port. In Compose the management port is published only for the `api` container so Prometheus/Grafana work locally; production should keep it internal. |
| Actuator exposure | Accepted limitation | `/actuator/**` is `permitAll` because it lives on the management port, which deployments must not expose publicly. Exposed endpoints: health, info, prometheus, metrics. |
| OpenAPI UI | Accepted limitation | Swagger UI and `/v3/api-docs` are public (documentation only, no data). Disable with `springdoc.swagger-ui.enabled=false` in production if undesired. |
| Headers | Implemented | Spring Security defaults (no-sniff, frame-deny, HSTS when behind TLS). TLS termination is expected at the load balancer. |
| Resource limits | Implemented (local) | JVM `MaxRAMPercentage=70`; Kafka heap capped; Postgres settings in Compose. |

## 7. Abuse and availability

| Topic | Status | Notes |
|---|---|---|
| Rate limiting | Implemented | Per-API-key token bucket on ingest (5000 events/s default, one-second burst) → 429 with `Retry-After`. Per node by design; a global limit would need a shared store. |
| Backpressure | Implemented | Kafka unavailable → 503 + `Retry-After` instead of buffering in memory; consumers pull at their own pace. |
| Poison messages | Implemented | Per-record dead-lettering; whole batches are never dropped. |
| Replay/audit endpoint cost | Accepted | Replay scans one customer-period; ADMIN-only. |

## 8. Summary

Implemented controls cover authentication, authorization, isolation, input validation, injection
resistance, secret handling and container hardening appropriate for a self-hosted single-team
deployment. Before exposing Tallyard to untrusted tenants on the public internet, the three
follow-ups must be closed: webhook SSRF protection, dependency/image vulnerability scanning, and
encryption-key rotation.
