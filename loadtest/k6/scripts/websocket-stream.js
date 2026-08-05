// websocket-stream.js
//
// Sustained location-update streaming from CONNECTIONS partners. Each VU is a
// distinct delivery partner (POOL entry) who:
//   1. opens a WebSocket, authenticates via JWT,
//   2. subscribes to its own order's topic,
//   3. pushes a location update every LOCATION_INTERVAL_MS for RUN_SECONDS,
//   4. counts how many updates are echoed back by the broker.
//
// Every accepted update produces exactly one broadcast to the topic, so the
// echo ratio is the end-to-end delivery ratio of the WS pipeline
// (rate limiter -> Redis geo -> JPA -> simple broker -> subscriber).
//
// Run the backend with a low LOCATION_UPDATE_MIN_INTERVAL_SECONDS (e.g. 0.05)
// so the per-partner rate limiter does not throttle this test.
import { Counter, Trend } from 'k6/metrics';
import {
  BASE,
  WS_BASE,
  CONNECTIONS,
  RUN_SECONDS,
  LOCATION_INTERVAL_MS,
  BASE_LAT,
  BASE_LNG,
  DRAIN_MS,
  CONNECT_TIMEOUT_MS,
  poolEntry,
} from '../lib/config.js';
import { login } from '../lib/login.js';
import { openStomp } from '../lib/sockjs-stomp.js';

const streamSent = new Counter('stream_sent');
const streamEchoed = new Counter('stream_echoed');
const streamDropped = new Counter('stream_dropped');
const streamDelivery = new Trend('stream_delivery_ratio', false);

export const options = {
  vus: CONNECTIONS,
  iterations: CONNECTIONS,
  thresholds: {
    // Fail loudly if the run produced no traffic (login/connect failure would
    // otherwise leave every other threshold without data and k6 passes it).
    stream_sent: ['count>=1'],
    // On a local run virtually every update should round-trip. Relax this if
    // running against a remote/hosted environment.
    stream_delivery_ratio: ['p(95)>=0.9'],
  },
};

export default function () {
  const creds = poolEntry(__VU - 1);
  const token = login(BASE, creds.email, creds.password);
  if (!token) return;

  const conn = openStomp(WS_BASE + '/ws/websocket', token);

  let sent = 0;
  let echoed = 0;
  let streamTimer = null;
  let failed = false;

  // Fail-loud watchdog: if the STOMP CONNECT handshake never completes, record
  // a failing sample and close instead of ending the iteration with no data.
  const watchdog = setTimeout(() => {
    if (!conn.connected) {
      failed = true;
      streamSent.add(0);
      streamDelivery.add(0);
      console.log(`VU ${__VU}: connection never reached CONNECTED; failing`);
      conn.close();
    }
  }, CONNECT_TIMEOUT_MS);

  conn.onConnected = () => {
    clearTimeout(watchdog);
    if (failed) return;

    conn.subscribe(`/topic/order/${creds.orderId}/location`);

    streamTimer = setInterval(() => {
      const seq = sent;
      sent += 1;
      conn.send(
        '/app/location-update',
        JSON.stringify({
          orderId: Number(creds.orderId),
          lat: BASE_LAT + seq * 0.00001,
          lng: BASE_LNG + seq * 0.00001,
        }),
      );
    }, LOCATION_INTERVAL_MS);

    setTimeout(() => {
      clearInterval(streamTimer);
      // Let in-flight echoes land before computing the ratio.
      setTimeout(() => {
        const dropped = Math.max(0, sent - echoed);
        streamSent.add(sent);
        streamEchoed.add(echoed);
        streamDropped.add(dropped);
        streamDelivery.add(sent > 0 ? echoed / sent : 1);
        conn.close();
      }, DRAIN_MS);
    }, RUN_SECONDS * 1000);
  };

  conn.onFrame = (headers) => {
    if (headers.destination && headers.destination.startsWith('/topic/order/')) {
      echoed += 1;
    }
  };

  conn.onError = (headers, body) => {
    console.log(`VU ${__VU}: STOMP ERROR ${headers.message || ''} ${body}`);
  };
}
