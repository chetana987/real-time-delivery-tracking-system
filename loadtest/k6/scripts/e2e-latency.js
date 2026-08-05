// e2e-latency.js
//
// True end-to-end WebSocket latency for a single order, measured over the
// complete pipeline: partner WS -> /app/location-update -> rate limiter ->
// Redis geo -> JPA write -> simple broker -> /topic/order/{id}/location ->
// customer WS.
//
// One VU opens TWO WebSocket connections (allowed by the k6 global event
// loop): a partner connection that SENDs updates and a customer connection
// that SUBSCRIBEs to the order topic. A per-message sequence number is encoded
// in the coordinates (lat = BASE_LAT + seq * 1e-5) and echoed back untouched,
// so the subscriber can attribute every echo to its send and compute
// latency = echo_receive_time - send_time.
//
// Run the backend with a low LOCATION_UPDATE_MIN_INTERVAL_SECONDS (e.g. 0.05)
// so the rate limiter allows every send at E2E_INTERVAL_MS.
import { Counter, Trend } from 'k6/metrics';
import {
  BASE,
  WS_BASE,
  EMAIL,
  PASSWORD,
  CUSTOMER_EMAIL,
  ORDER_ID,
  E2E_SENDS,
  E2E_INTERVAL_MS,
  E2E_BUFFER_MS,
  E2E_START_BUFFER_MS,
  BASE_LAT,
  BASE_LNG,
} from '../lib/config.js';
import { login } from '../lib/login.js';
import { openStomp, when } from '../lib/sockjs-stomp.js';

const e2eLatency = new Trend('e2e_latency_ms', true);
const e2eSent = new Counter('e2e_sent');
const e2eEchoed = new Counter('e2e_echoed');
const e2eMissing = new Counter('e2e_missing');

export const options = {
  vus: 1,
  iterations: 1,
  thresholds: {
    // Every send must come back: this turns rate-limited or failed runs into a
    // hard failure instead of a latency pass on a handful of samples.
    e2e_echoed: ['count==' + E2E_SENDS],
    // Localhost is typically < 100ms; relax for remote/hosted runs.
    e2e_latency_ms: ['p(95)<500'],
  },
};

export default function () {
  const orderId = Number(ORDER_ID);
  const partnerToken = login(BASE, EMAIL, PASSWORD);
  const customerToken = login(BASE, CUSTOMER_EMAIL, PASSWORD);
  if (!partnerToken || !customerToken) return;

  const partner = openStomp(WS_BASE + '/ws/websocket', partnerToken);
  const customer = openStomp(WS_BASE + '/ws/websocket', customerToken);

  const sendTimes = new Map();
  let sent = 0;
  let echoed = 0;
  let started = false;
  let sendTimer = null;

  customer.onConnected = () => {
    customer.subscribe(`/topic/order/${orderId}/location`);

    when(
      () => partner.connected && customer.connected,
      () => {
        // Give the SUBSCRIBE a moment to reach the broker before the first send.
        setTimeout(() => {
          if (started) return;
          started = true;
          sendTimer = setInterval(() => {
            if (sent >= E2E_SENDS) return;
            const seq = sent;
            sent += 1;
            sendTimes.set(seq, Date.now());
            partner.send(
              '/app/location-update',
              JSON.stringify({
                orderId,
                lat: BASE_LAT + seq * 0.00001,
                lng: BASE_LNG + seq * 0.00001,
              }),
            );
            if (sent >= E2E_SENDS) {
              clearInterval(sendTimer);
              // Drain window for the final echoes, then report and close.
              setTimeout(() => {
                e2eSent.add(sent);
                e2eEchoed.add(echoed);
                e2eMissing.add(sendTimes.size);
                partner.close();
                customer.close();
              }, E2E_BUFFER_MS);
            }
          }, E2E_INTERVAL_MS);
        }, E2E_START_BUFFER_MS);
      },
      () => {
        console.log('e2e: partner/customer connections never became ready');
        partner.close();
        customer.close();
      },
    );
  };

  customer.onFrame = (headers, body) => {
    if (!headers.destination || !headers.destination.startsWith('/topic/order/')) return;
    let msg;
    try {
      msg = JSON.parse(body);
    } catch {
      return;
    }
    const seq = Math.round((msg.lat - BASE_LAT) / 0.00001);
    const t0 = sendTimes.get(seq);
    if (t0 !== undefined) {
      e2eLatency.add(Date.now() - t0);
      echoed += 1;
      sendTimes.delete(seq);
    }
  };
}
