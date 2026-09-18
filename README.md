# Chetana Delivery — Delivery Tracking System

Real-time food/ride delivery tracking platform: a Spring Boot REST API (orders,
restaurants, menu, live location tracking over WebSocket/STOMP, JWT auth) with a
React/Vite dashboard and k6 load tests.

<!-- Replace OWNER/REPO below with your GitHub repository path, e.g. yourname/chetana-delivery-tracking -->
[![CI](https://github.com/chetana987/real-time-delivery-tracking-system/actions/workflows/ci.yml/badge.svg)](https://github.com/chetana987/real-time-delivery-tracking-system/actions/workflows/ci.yml)

## Features

- **Role-based access** — CUSTOMER, DELIVERY_PARTNER and ADMIN accounts with
  self-registration (ADMIN is seeded, not self-registrable).
- **Order lifecycle** — place orders, delivery partners browse available orders,
  accept them and drive the status pipeline (`PLACED → ACCEPTED → PICKED_UP →
  DELIVERED`), with optimistic locking against concurrent accepts.
- **Real-time tracking** — delivery partners stream live location via
  WebSocket/STOMP (SockJS), rate-limited with a Redis token bucket; customers
  follow orders on a Leaflet map with smooth marker movement.
- **Pagination, sorting & filtering** — every list endpoint returns a
  `PageResponse` envelope; orders, restaurants and menus are filterable and
  sortable (see [PAGINATION.md](PAGINATION.md)).
- **JWT security** — signed HS256 access tokens, Spring Security method-level
  authorization, global `@RestControllerAdvice` error mapping.
- **Production-minded ops** — Docker Compose for MySQL + Redis + backend,
  Testcontainers integration tests, GitHub Actions CI, Swagger/OpenAPI docs.

## Tech stack

| Layer    | Technology |
|----------|------------|
| Language | Java 21 |
| Backend  | Spring Boot 3.5.4 (Web, Security, Data JPA, Data Redis, WebSocket, Validation) |
| Build    | Maven 3.9+ (Spring Boot parent POM; Docker build pins `maven:3.9-eclipse-temurin-21`) |
| Database | MySQL 8 (JPA/Hibernate) |
| Cache    | Redis 7 (rate limiting, partner presence) |
| Realtime | WebSocket + STOMP, SockJS |
| Auth     | JJWT 0.12.6 |
| Docs     | springdoc-openapi 2.8.9 (Swagger UI) |
| Frontend | React 18, Vite 5, Tailwind CSS 4, Leaflet |
| Testing  | JUnit 5, Spring Test, Testcontainers (MySQL 8), JaCoCo, k6 |

## Architecture

```
frontend/          React + Vite + Tailwind SPA (customer & partner dashboards, map)
loadtest/          k6 load-test scripts + seed helper
src/main/java/com/deliverytracking/
  ├── config/      Security, JWT filter + WebSocket auth, STOMP, OpenAPI, admin seeder
  ├── controller/  REST controllers (auth, orders, restaurants, locations) + STOMP tracker
  ├── service/     Business logic, pagination support, rate limiting, geo, presence
  ├── repository/  Spring Data JPA repositories + JpaSpecificationExecutor filter specs
  ├── entity/      JPA entities (Order, Restaurant, MenuItem, User, LocationUpdate, ...)
  ├── dto/         Request/response records, PageResponse/PageParams/OrderFilter
  └── exception/   Domain exceptions + global handler (400/401/403/404/409/500)
src/test/          Unit tests + @SpringBootTest integration tests (Testcontainers MySQL)
```

High-level request flow: JWT filter authenticates → `@PreAuthorize` enforces the
role → controller binds/validates query params → service composes JPA
`Specification`s into a `Page` → `PageResponse` returned. Location updates are
pushed over STOMP `/app/location-update` (rate-limited), persisted, and
broadcast back to subscribers of the order's destination topic.

## Installation

Prerequisites: **JDK 21**, **Maven 3.9+**, **Docker** (for integration tests and
the Compose stack), **Node.js 20+** (for the frontend).

```bash
# 1. Clone and prepare environment variables
cp .env.example .env        # then edit values if needed

# 2. Backend — compile and test
mvn clean verify

# 3. Frontend — install and build
cd frontend && npm install && npm run build && cd ..
```

## Environment variables

All configuration is externalised via environment variables (see
[`application.yml`](src/main/resources/application.yml) and
[`.env.example`](.env.example)). Never commit real values — use `.env` locally
or GitHub Secrets.

| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_PORT` | `8080` | Backend HTTP port |
| `MYSQL_HOST` / `MYSQL_PORT` | `localhost` / `3306` | MySQL host/port |
| `MYSQL_DB` | `delivery_tracking` | Database name |
| `MYSQL_USER` / `MYSQL_PASSWORD` | `root` / `root` | MySQL credentials |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` | Redis host/port |
| `REDIS_PASSWORD` | *(empty)* | Redis password |
| `JWT_SECRET` | *(required — no default)* | HS256 signing key, **≥ 32 bytes** (e.g. `openssl rand -hex 32`), must be set or the app refuses to start |
| `JWT_EXPIRATION_MS` | `86400000` | Access-token lifetime in ms |
| `PARTNER_SEARCH_RADIUS_KM` | `10` | Radius to find nearby online partners on placement |
| `LOCATION_UPDATE_MIN_INTERVAL_SECONDS` | `2` | Min interval between partner location updates |

## Running locally

Start MySQL and Redis (e.g. via the Compose services), then:

```bash
export JWT_SECRET=$(openssl rand -hex 32)  # or use your .env value
mvn spring-boot:run          # backend on http://localhost:8080
cd frontend && npm run dev   # frontend on http://localhost:5173 (proxies /api)
```

> `JWT_SECRET` is required — the backend will not start without it.

The admin account is seeded automatically on first start:

| Email | Password | Role |
|-------|----------|------|
| `admin@delivery.com` | `admin123` | ADMIN |

Register CUSTOMER / DELIVERY_PARTNER accounts from the **Register** page.

## Docker setup

```bash
cp .env.example .env
docker compose up --build     # MySQL + Redis + backend, health-checked
```

The backend image is built with a multi-stage Dockerfile (Maven build stage →
minimal JRE runtime) and connects to MySQL/Redis over the Compose network.
Swagger UI: `http://localhost:8080/swagger-ui.html`.

## API documentation

Interactive OpenAPI docs are auto-generated and served at:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

The documentation lists every endpoint, query parameter, request/response
schema and the bearer-token auth requirement.

## Testing

`mvn clean verify` runs the full suite — unit tests plus `@SpringBootTest`
integration tests that boot the real stack against a Testcontainers MySQL 8
container (skipped automatically when Docker/MySQL are unavailable). See
[TESTING.md](TESTING.md) for the test matrix, and `loadtest/` for k6 scenarios.

## GitHub Actions

Continuous integration runs on every push to `main` and every PR targeting
`main` (see [CI.md](CI.md)):

- **build** — JDK 21, cached Maven repo, `mvn clean verify` (red on any compile
  error or failing test).
- **docker-build** — verifies the Docker image builds; never pushed.

## License

[MIT](LICENSE) — see the LICENSE file.
