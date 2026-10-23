// k6 load profile for POST /v1/events.
//   k6 run -e API_KEY=ty_... -e BASE_URL=http://localhost:8080 loadtest/ingest.js
// Each VU posts batches of BATCH events for a demo customer; ~5 % of events are duplicates so the
// deduplication path is exercised under load. Thresholds encode the targets from docs/performance.
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const KEY = __ENV.API_KEY;
const BATCH = Number(__ENV.BATCH || 100);
const CUSTOMERS = Number(__ENV.CUSTOMERS || 20);
const RATE = Number(__ENV.RATE || 20); // batches per second → events/s = RATE × BATCH

export const options = {
  scenarios: {
    ingest: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: __ENV.DURATION || '60s',
      preAllocatedVUs: 20,
      maxVUs: 200,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{endpoint:ingest}': ['p(95)<150', 'p(99)<300'],
    accepted_events: ['count>0'],
  },
};

const accepted = new Counter('accepted_events');
const batchLatency = new Trend('batch_latency_ms', true);
const run = Date.now();

export default function () {
  const customer = 'cust-' + String(1 + Math.floor(Math.random() * CUSTOMERS)).padStart(3, '0');
  const now = new Date().toISOString();
  const events = [];
  for (let i = 0; i < BATCH; i++) {
    const id = `k6-${run}-${__VU}-${__ITER}-${i}`;
    const kind = Math.random();
    if (kind < 0.8) {
      events.push({ eventId: id, eventType: 'request', subject: customer, occurredAt: now, properties: { tokens: 50 + Math.floor(Math.random() * 2000) } });
    } else if (kind < 0.9) {
      events.push({ eventId: id, eventType: 'storage', subject: customer, occurredAt: now, properties: { gb: 10 + Math.floor(Math.random() * 100) } });
    } else {
      events.push({ eventId: id, eventType: 'heartbeat', subject: customer, occurredAt: now, properties: { device_id: 'dev-' + Math.floor(Math.random() * 30) } });
    }
    if (Math.random() < 0.05) events.push(events[events.length - 1]); // duplicate
  }
  const res = http.post(`${BASE}/v1/events`, JSON.stringify({ events }), {
    headers: { 'Content-Type': 'application/json', 'X-Api-Key': KEY },
    tags: { endpoint: 'ingest' },
  });
  batchLatency.add(res.timings.duration);
  const ok = check(res, { 'status 202': (r) => r.status === 202 });
  if (ok) accepted.add(res.json('accepted'));
  else if (res.status === 429 || res.status === 503) sleep(Number(res.headers['Retry-After'] || 1));
}
