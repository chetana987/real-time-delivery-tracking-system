// rate-limit.js
//
// Verifies the per-partner token-bucket rate limiter on /app/location-update
// (RateLimiterService + TrackingController).
//
// Phase 1 (burst): fire BURST_COUNT updates at BURST_GAP_MS apart. The bucket
// holds capacity 1 and refills at 1/LOCATION_UPDATE_MIN_INTERVAL_SECONDS per
// second, so with the default 2s interval only ~1 update is admitted.
// Phase 2 (recovery): wait RECOVERY_MS (longer than the min interval), send one
// more update and assert it is accepted (bucket has refilled).
//
// IMPORTANT: the limiter drops excess updates silently - there is no HTTP 429
// and no STOMP ERROR frame (see TrackingController). The only observable proof
// is the broker echo count, which is what this script measures.
//
// Run against the DEFAULT min interval (LOCATION_UPDATE_MIN_INTERVAL_SECONDS
// unset or >= 1). With a very low interval (e.g. 0.05) the burst may be fully
// admitted and the `rate_burst_dropped` threshold will fail by design.
import { Counter } from 'k6/metrics';
import {
  BASE,
  WS_BASE,
  EMAIL,
  PASSWORD,
  ORDER_ID,
  BURST_COUNT,
  BURST_GAP_MS,
  RECOVERY_MS,
  DRAIN_MS,
  BASE_LAT,
  BASE_LNG,
} from '../lib/config.js';
import { login } from '../lib/login.js';
import { openStomp } from '../lib/sockjs-stomp.js';

const burstSent = new Counter('rate_burst_sent');
const burstEchoed = new Counter('rate_burst_echoed');
const burstDropped = new Counter('rate_burst_dropped');
const recoveryAccepted = new Counter('rate_recovery_accepted');

export const options = {
  vus: 1,
  iterations: 1,
  thresholds: {
    // A burst faster than the min interval MUST be throttled.
    rate_burst_dropped: ['count>=1'],
    // After RECOVERY_MS the bucket must have refilled.
    rate_recovery_accepted: ['count==1'],
  },
};

export default function () {
  const orderId = Number(ORDER_ID);
  const token = login(BASE, EMAIL, PASSWORD);
  if (!token) return;

  const conn = openStomp(WS_BASE + '/ws/websocket', token);

  let echoed = 0;
  let burstCount = 0;

  conn.onConnected = () => {
    conn.subscribe(`/topic/order/${orderId}/location`);

    // Phase 1: burst.
    const burstTimer = setInterval(() => {
      conn.send(
        '/app/location-update',
        JSON.stringify({ orderId, lat: BASE_LAT, lng: BASE_LNG }),
      );
      burstCount += 1;
      if (burstCount >= BURST_COUNT) {
        clearInterval(burstTimer);
        setTimeout(recordBurst, DRAIN_MS);
      }
    }, BURST_GAP_MS);

    function recordBurst() {
      const dropped = Math.max(0, burstCount - echoed);
      burstSent.add(burstCount);
      burstEchoed.add(echoed);
      burstDropped.add(dropped);
      console.log(`rate-limit: burst sent=${burstCount} echoed=${echoed} dropped=${dropped}`);

      const echoBase = echoed;

      // Phase 2: recovery.
      setTimeout(() => {
        conn.send(
          '/app/location-update',
          JSON.stringify({ orderId, lat: BASE_LAT, lng: BASE_LNG }),
        );
        setTimeout(() => {
          if (echoed > echoBase) {
            recoveryAccepted.add(1);
            console.log('rate-limit: recovery update accepted');
          } else {
            console.log('rate-limit: recovery update was still throttled');
          }
          conn.close();
        }, DRAIN_MS);
      }, RECOVERY_MS);
    }
  };

  conn.onFrame = () => {
    echoed += 1;
  };

  conn.onError = (headers, body) => {
    console.log(`STOMP ERROR ${headers.message || ''} ${body}`);
  };
}
