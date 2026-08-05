// Shared, env-overridable configuration for all k6 scripts.
// Every value can be set from the CLI with -e NAME=value.

export const BASE = __ENV.BASE_URL || 'http://localhost:8080';
export const WS_BASE = BASE.replace(/^http/, 'ws');
export const PASSWORD = __ENV.PASSWORD || 'Loadtest1!';

// Default single-user credentials / order used by the e2e, rate-limit and
// accept-race scripts when POOL is not supplied.
export const EMAIL = __ENV.EMAIL || 'partner1@loadtest.local';
export const CUSTOMER_EMAIL = __ENV.CUSTOMER_EMAIL || 'customer1@loadtest.local';
export const RESTAURANT_ID = __ENV.RESTAURANT_ID || '';
export const ORDER_ID = __ENV.ORDER_ID || '';

// WebSocket streaming / connection tests
export const CONNECTIONS = Number(__ENV.CONNECTIONS || 20);
export const RUN_SECONDS = Number(__ENV.RUN_SECONDS || 30);
export const LOCATION_INTERVAL_MS = Number(__ENV.LOCATION_INTERVAL_MS || 200);

// Coordinates used to encode the message sequence number (see README).
export const BASE_LAT = Number(__ENV.BASE_LAT || 12.9716);
export const BASE_LNG = Number(__ENV.BASE_LNG || 77.5946);

// End-to-end latency test
export const E2E_SENDS = Number(__ENV.E2E_SENDS || 50);
export const E2E_INTERVAL_MS = Number(__ENV.E2E_INTERVAL_MS || 100);
export const E2E_BUFFER_MS = Number(__ENV.E2E_BUFFER_MS || 3000);
export const E2E_START_BUFFER_MS = Number(__ENV.E2E_START_BUFFER_MS || 500);

// Rate-limiter test
export const BURST_COUNT = Number(__ENV.BURST_COUNT || 10);
export const BURST_GAP_MS = Number(__ENV.BURST_GAP_MS || 50);
export const RECOVERY_MS = Number(__ENV.RECOVERY_MS || 2500);

// Accept-race test
export const RACE_PARTNERS = Number(__ENV.RACE_PARTNERS || 10);

// REST tests
export const AUTH_USERS = Number(__ENV.AUTH_USERS || 50);

// Extra time (ms) left after the last send so in-flight echoes arrive.
export const DRAIN_MS = Number(__ENV.DRAIN_MS || 1000);

// How long a script waits for a WebSocket to reach STOMP CONNECTED before it
// records a failure and gives up (fail-loud watchdog).
export const CONNECT_TIMEOUT_MS = Number(__ENV.CONNECT_TIMEOUT_MS || 10000);

// Pool of `email:password:orderId` entries, ';'-separated, one per partner.
// Used by the streaming and accept-race tests so every VU gets a distinct
// partner (each partner has its own rate-limit bucket and assigned order).
export const POOL_STRING = __ENV.POOL || '';

export function poolEntry(index) {
  const entries = POOL_STRING.split(';')
    .map((s) => s.trim())
    .filter(Boolean)
    .map((s) => {
      const [email, password, orderId] = s.split(':');
      return { email, password, orderId };
    });
  if (entries.length === 0) {
    return { email: EMAIL, password: PASSWORD, orderId: ORDER_ID };
  }
  return entries[index % entries.length];
}
