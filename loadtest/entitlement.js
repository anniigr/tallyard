// k6 load profile for POST /v1/entitlements/check (the hot path).
//   k6 run -e API_KEY=ty_... loadtest/entitlement.js
import http from 'k6/http';
import { check } from 'k6';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const KEY = __ENV.API_KEY;
const CUSTOMERS = Number(__ENV.CUSTOMERS || 20);

export const options = {
  scenarios: {
    checks: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.RATE || 500),
      timeUnit: '1s',
      duration: __ENV.DURATION || '60s',
      preAllocatedVUs: 50,
      maxVUs: 300,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.001'],
    http_req_duration: ['p(50)<10', 'p(99)<20'],
  },
};

export default function () {
  const customer = 'cust-' + String(1 + Math.floor(Math.random() * CUSTOMERS)).padStart(3, '0');
  const meter = Math.random() < 0.7 ? 'api_calls' : 'tokens';
  const res = http.post(`${BASE}/v1/entitlements/check`, JSON.stringify({ subject: customer, meter, requested: 1 }), {
    headers: { 'Content-Type': 'application/json', 'X-Api-Key': KEY },
  });
  check(res, { 'status 200': (r) => r.status === 200, 'has decision': (r) => r.json('decision') !== undefined });
}
