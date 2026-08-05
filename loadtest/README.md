# Delivery Tracking System — Load Testing

[k6](https://grafana.com/docs/k6/latest/) suite for the delivery-tracking
backend. Every test exercises a real, running stack (MySQL + Redis + Spring
Boot) — the same stack you get from `docker compose up -d`.

## Why k6

- One language (JavaScript) for HTTP **and** WebSocket load.
- The `k6/websockets` module uses a **global event loop**, so a single VU can
  hold two concurrent WebSocket connections. That is what makes the
  end-to-end latency test possible: one VU plays both the partner (sender) and
  the customer (subscriber).
- Built-in WebSocket metrics (`ws_connecting`, `ws_msgs_sent`,
  `ws_msgs_received`, `ws_ping`, `ws_sessions`, `ws_session_duration`) plus
  scripted metrics and `thresholds`.
- The backend uses **SockJS + STOMP**; k6 does the STOMP framing in the test
  script (`lib/sockjs-stomp.js`), so no extra tooling is needed.

## Folder layout

```
loadtest/
├── README.md
├── seed.sql                 # restaurant + menu (ADMIN-only data, inserted via SQL)
├── seed.sh                  # registers users, places & assigns orders via the API
└── k6/
    ├── lib/
    │   ├── config.js        # shared, env-overridable config (all -e flags)
    │   ├── login.js         # POST /api/auth/login helper
    │   └── sockjs-stomp.js  # minimal STOMP-over-SockJS client
    └── scripts/
        ├── websocket-stream.js   # sustained partner streaming + delivery ratio
        ├── e2e-latency.js        # true end-to-end WS latency (2 sockets/VU)
        ├── rate-limit.js         # token-bucket limiter burst + recovery
        └── accept-race.js        # concurrent order accepts (optimistic locking)
```

## Prerequisites

1. **Stack up and healthy**
   ```bash
   docker compose up -d
   docker compose ps            # wait until backend is "healthy"
   curl http://localhost:8080/api/health
   ```
   k6 requires `k6 >= 0.52` (global event loop is default).

2. **Seed data** — two steps:
   ```bash
   chmod +x loadtest/seed.sh
   ./loadtest/seed.sh            # registers users, places/accepts orders
   ```
   `seed.sh` applies `seed.sql` automatically if no restaurant exists, then
   registers 1 customer, `PARTNERS` partners (each with an **ACCEPTED** order
   they are assigned to), `RACE_PARTNERS` race partners, and one **PLACED**
   (unassigned) order for the accept-race. It prints the `POOL` values to pass
   to k6.

## Important: the rate limiter has no HTTP status

`TrackingController` limits `/app/location-update` with a Redis token bucket
(`RateLimiterService`, capacity 1). Excess updates are **silently dropped**
with a `log.warn("Rate limited: ...")` — there is **no HTTP 429** and **no
STOMP ERROR frame**. That is why the load tests never assert on status codes
for the limiter; they assert on the **broker echo count** (one broadcast per
accepted update) and on Redis token state (`ratelimit:location:{userId}` hash,
`tokens` between 0 and 1). If your requirement is a visible 429, that is a
backend gap, not a test gap — flag it to the team.

## Running the tests

All settings come from `-e NAME=value` (see `k6/lib/config.js`). Substitute
the `POOL`/`ORDER_ID` values printed by `seed.sh`.

### 1. Streaming throughput (`websocket-stream.js`)

`CONNECTIONS` partners each stream a location update every
`LOCATION_INTERVAL_MS` for `RUN_SECONDS`, and every echo is counted.

```bash
k6 run -e POOL="$STREAM_POOL" \
       -e CONNECTIONS=20 -e LOCATION_INTERVAL_MS=200 -e RUN_SECONDS=30 \
       loadtest/k6/scripts/websocket-stream.js
```

Set the backend min interval low so the per-partner limiter does not throttle
the stream:

```bash
LOCATION_UPDATE_MIN_INTERVAL_SECONDS=0.05 docker compose up -d --build backend
```

What it proves: the WS pipeline (`rate limiter -> Redis geo -> JPA -> simple
broker -> subscriber`) sustains `CONNECTIONS / LOCATION_INTERVAL_MS * 1000`
updates/sec with a near-100% delivery ratio.

### 2. End-to-end latency (`e2e-latency.js`)

One VU, **two** WebSocket connections: the partner SENDs and the customer
SUBSCRIBEs to `/topic/order/{id}/location`. A sequence number is encoded in
the coordinates (`lat = BASE_LAT + seq * 1e-5`) and echoed back untouched, so
each echo is attributed to its send.

```bash
k6 run -e EMAIL=partner1@loadtest.local -e CUSTOMER_EMAIL=customer1@loadtest.local \
       -e ORDER_ID="$FIRST_ORDER" -e E2E_SENDS=50 -e E2E_INTERVAL_MS=100 \
       loadtest/k6/scripts/e2e-latency.js
```

**How latency is measured**: `t0 = Date.now()` immediately before the partner
SENDs; the customer's `message` handler parses the echo, recovers `seq` from
`lat`, and records `latency = Date.now() - t0`. This includes full network +
broker + Redis + JPA + WS round-trip. The server-side `timestamp` field in the
echo is the source of truth for clock-skew checks; local `Date.now()` deltas
need no clock sync.

Run against `LOCATION_UPDATE_MIN_INTERVAL_SECONDS=0.05` so every send passes
the limiter; otherwise `e2e_missing` grows.

### 3. Rate limiter (`rate-limit.js`)

Bursts `BURST_COUNT` updates at `BURST_GAP_MS` apart, asserts most are
dropped, waits `RECOVERY_MS`, sends once more and asserts the bucket refilled.

```bash
k6 run -e EMAIL=partner1@loadtest.local -e PASSWORD='Loadtest1!' -e ORDER_ID="$FIRST_ORDER" \
       -e BURST_COUNT=10 -e BURST_GAP_MS=50 -e RECOVERY_MS=2500 \
       loadtest/k6/scripts/rate-limit.js
```

Run against the **default** `LOCATION_UPDATE_MIN_INTERVAL_SECONDS=2` so the
burst is genuinely throttled. Optional Redis-side confirmation:

```bash
docker compose exec redis redis-cli -a delivery-redis-dev --no-auth-warning \
  HGETALL "ratelimit:location:<partnerUserId>"
# -> tokens should sit between 0 and 1 after a burst
```

### 4. Accept race (`accept-race.js`)

`RACE_PARTNERS` partners fire `PATCH /api/orders/{id}/accept` at the same
**PLACED** order. Optimistic locking (`@Version` + `OrderService.acceptOrder`)
guarantees exactly one winner.

```bash
k6 run -e POOL="$RACE_POOL" -e ORDER_ID="$RACE_ORDER_ID" -e RACE_PARTNERS=10 \
       loadtest/k6/scripts/accept-race.js
```

Passing thresholds: `race_wins == 1` and `race_conflicts == RACE_PARTNERS - 1`.
**Re-seed the order between runs** (it becomes ACCEPTED after the first race).

## Metrics — how to read the output

| Metric | Meaning | Healthy signal |
| --- | --- | --- |
| `stream_sent` / `stream_delivery_ratio` | echoes / sends per VU | sent >= 1 (fails if no traffic) and p(95) >= 0.9 locally |
| `e2e_latency_ms` | full WS round-trip latency | p95 < 500ms localhost |
| `e2e_echoed` / `e2e_missing` | sends that returned / had no echo | `e2e_echoed == E2E_SENDS` (fails loudly if any send is dropped or throttled) |
| `rate_burst_dropped` | updates rejected by the limiter | >= 1 on a fast burst |
| `rate_recovery_accepted` | update accepted after the wait | == 1 |
| `race_wins` / `race_conflicts` | accept outcomes | 1 / RACE_PARTNERS-1 |
| `ws_sessions`, `ws_msgs_received`, `ws_msgs_sent` | built-in WS counters | no 100% failure |
| `http_req_duration` | REST calls (login/accept) | p95 < 500ms localhost |

Threshold failures usually mean one of: backend min-interval not lowered for
stream/e2e, order not re-seeded for the race, or `POOL`/`ORDER_ID` wrong.

## Turning results into resume bullets

- "Engineered a 4-part k6 load-testing suite (streaming throughput, end-to-end
  WebSocket latency, Redis token-bucket rate limiting, and concurrent-accept
  race conditions) for a Spring Boot delivery-tracking app, asserting delivery
  ratios, p95 latency, limiter drop/recovery, and exactly-one-winner on
  `@Version`-locked accepts."
- "Measured true end-to-end location latency by running dual WebSocket
  connections per k6 VU with sequence numbers embedded in message payloads —
  eliminating client/server clock-sync issues."
- "Identified and documented a production gap: the location rate limiter drops
  excess updates silently (no HTTP 429 / STOMP error), proving throttling via
  broker echo counts and Redis token state instead."

## Config reference (`-e NAME=value`)

`BASE_URL` (default `http://localhost:8080`), `PASSWORD`, `EMAIL`,
`CUSTOMER_EMAIL`, `ORDER_ID`, `CONNECTIONS` (20), `RUN_SECONDS` (30),
`LOCATION_INTERVAL_MS` (200), `BASE_LAT`/`BASE_LNG` (12.9716 / 77.5946),
`E2E_SENDS` (50), `E2E_INTERVAL_MS` (100), `E2E_BUFFER_MS` (3000),
`E2E_START_BUFFER_MS` (500), `BURST_COUNT` (10), `BURST_GAP_MS` (50),
`RECOVERY_MS` (2500), `DRAIN_MS` (1000), `RACE_PARTNERS` (10), and `POOL`
(`email:password:orderId` entries, `;`-separated).
