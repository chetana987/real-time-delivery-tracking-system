# Testing Suite — Delivery Tracking System

Two layers of automated tests cover the REST API, business rules and the optimistic-lock
acceptance race:

| Layer | Framework | Real DB? | Speed |
|-------|-----------|----------|-------|
| Unit tests (`src/test/java/com/deliverytracking/entity`, `.../service`) | JUnit 5 + Mockito | No | milliseconds |
| Integration tests (`src/test/java/com/deliverytracking/integration`) | JUnit 5 + SpringBootTest + MockMvc | Yes (Testcontainers MySQL 8, or local MySQL) | seconds |

---

## 1. Why two kinds of tests?

**Unit tests** exercise one class in isolation. Dependencies are replaced with Mockito mocks, so
they are instant, deterministic and need no database. Example: `OrderServiceAcceptanceTest`
mocks `OrderRepository`/`UserRepository`/`EntityManager` and asserts that `acceptOrder` moves a
`PLACED` order to `ACCEPTED`, assigns the partner and calls `entityManager.flush()`.

**Integration tests** start the full Spring context (real controllers, security filter chain,
JPA repositories, `GlobalExceptionHandler`, `AdminDataSeeder`) and hit HTTP endpoints through
`MockMvc`. They verify the *whole stack*: status codes, JSON bodies, persistence side-effects and
authorization. Example: `OrderFlowIntegrationTest` registers a customer and a partner through the
real `/api/auth/register` endpoint, places an order via `POST /api/orders`, accepts it via
`PATCH /api/orders/{id}/accept`, and finally reads the row back with `OrderRepository` to prove
the status was persisted.

A bug like "service returns 200 but never flushes the row" can only be caught by the integration
layer; a logic bug in the transition map is caught fastest by the unit layer.

## 2. Why Mockito?

- Lets us isolate `OrderService` from JPA/Redis/network: we stub exactly one repository response
  per scenario.
- Lets us force failure paths that are hard to reproduce in a real DB, e.g. making
  `entityManager.flush()` throw `ObjectOptimisticLockingFailureException` to simulate the version
  conflict race (`OptimisticLockingTest`).
- No Spring context, no MySQL, no Redis → the four unit test classes run in a few milliseconds.

## 3. Why mock the repositories (instead of an in-memory DB like H2)?

- The project uses MySQL-specific SQL and schema; H2 would not behave identically (dialect,
  `@Version` handling, constraints). Mocking avoids a false-green setup.
- The real persistence behavior is exercised properly by the Testcontainers integration layer,
  so mocking repositories in unit tests loses nothing we care about there.

## 4. Why don't unit tests need MySQL (or Docker) at all?

A unit test never touches `JpaRepository` — `OrderRepository`, `UserRepository` etc. are Mockito
mocks. Pure logic classes such as `Order.transitionTo` and `JwtService` have no dependencies on
the database. So unit tests run on any machine that has only Java + Maven.

## 5. Why do integration tests use the real Spring context?

The point of an integration test is to verify wiring: JWT filter → `@PreAuthorize` → controller →
service → repository → real MySQL, plus the `@RestControllerAdvice` exception-to-HTTP mapping.
`@SpringBootTest` + `@AutoConfigureMockMvc` boots that stack without needing a real HTTP server or
web browser. `spring.security` runs for real: `POST /api/orders` without a token returns 401, a
CUSTOMER calling `PATCH .../accept` returns 403, etc.

## 6. Database strategy (Testcontainers preferred, local MySQL fallback)

`AbstractIntegrationTest` decides at runtime:

1. **Docker available** → starts a real `mysql:8.0` container and points the datasource at it.
2. **No Docker, local MySQL reachable** → connects to
   `jdbc:mysql://localhost:3306/delivery_tracking_test` (override with `TEST_DB_URL`,
   `TEST_DB_USER`, `TEST_DB_PASSWORD`).
3. **Neither** → the integration classes are *aborted (skipped)* so `mvn test` still succeeds on
   machines with no database. Unit tests always run.

The schema is recreated per run via `spring.jpa.hibernate.ddl-auto=create-drop`, and the seeded
`admin@delivery.com` / `admin123` account (from `AdminDataSeeder`) is used for ADMIN-only flows.
Redis is intentionally **not** required: `PartnerGeoService` and `RateLimiterService` fail open,
so order placement still works.

## 7. What each file covers

```
src/test/java/com/deliverytracking/
├── OrderConcurrencyTest.java                        # 2 partners race to accept 1 order → exactly 1 wins
├── entity/
│   └── OrderStateTransitionTest.java               # valid/invalid/boundary transitions of Order.transitionTo
├── exception/
│   └── GlobalExceptionHandlerValidationTest.java   # ConstraintViolationException → 400 fieldErrors; 500 hides internals
├── service/
│   ├── OrderServiceAcceptanceTest.java             # acceptOrder: success, order missing, partner missing,
│   │                                               #   already accepted, flush version-conflict
│   ├── OptimisticLockingTest.java                  # flush conflict wrapping + OptimisticLockConflict → 409 mapping
│   ├── JwtServiceTest.java                         # generate, parse claims, validate, expired, wrong signature, malformed
│   └── PagingSupportTest.java                      # page/size/sortBy/direction → Pageable: defaults, case-insensitive
│                                                   #   direction, invalid field/direction → BadRequestException
└── integration/
    ├── AbstractIntegrationTest.java                # shared SpringBootTest + MockMvc + DB selection + helpers
    ├── AuthFlowIntegrationTest.java                # register, duplicate email, ADMIN block, validation, login, bad login
    ├── OrderFlowIntegrationTest.java               # place, accept (incl. 409 race), status pipeline, 400/403 paths
    ├── OrderCancellationIntegrationTest.java        # cancel own PLACED (200), other's order (403), ACCEPTED /
    │                                               #   DELIVERED / already CANCELLED (400), partner & admin (403),
    │                                               #   leaves available pool, concurrent cancel race, location
    │                                               #   updates rejected after cancellation
    ├── RestaurantCrudIntegrationTest.java          # restaurant + menu CRUD, auth rules, validation, delete-with-orders
    ├── GlobalExceptionHandlingIntegrationTest.java # 404/400/401/403 mappings for the @RestControllerAdvice
    ├── OpenApiDocumentationIntegrationTest.java    # /v3/api-docs metadata + tags + JWT scheme, pagination query params
    ├── PaginationFilteringIntegrationTest.java     # page metadata, paging, sorting, filters, validation errors
    └── LocationTrackingIntegrationTest.java        # latest location: 200 with update, 404 when none, 404 missing
                                                    #   order, 400 invalid id, 403 non-participant, 401 unauth
```

### Business rules verified

- **Order state machine** (`Order.ALLOWED_TRANSITIONS`): `PLACED → ACCEPTED → PICKED_UP →
  OUT_FOR_DELIVERY → DELIVERED` plus `PLACED → CANCELLED`; every other skip, repeat, null, and
  transition out of `DELIVERED` / `CANCELLED` throws `InvalidStateTransitionException` → HTTP 400.
- **Acceptance race**: the first partner wins (`200`, partner assigned); the second gets
  `OptimisticLockConflictException` → HTTP 409. The `@Version` column protects the update even
  when two requests pass the status check concurrently (covered by `OrderConcurrencyTest`).
- **Status updates** may only be done by the assigned partner (`403` otherwise); `ACCEPTED` is
  not settable via the status endpoint (`400`).
- **Cancellation** (`PATCH /api/orders/{id}/cancel`, CUSTOMER only): a customer may cancel
  their own order only from `PLACED` (`PLACED → CANCELLED` → `200`); cancelling someone else's
  order → `403`; cancelling an `ACCEPTED` / in-flight / `DELIVERED` order or one that is already
  `CANCELLED` → `400` (state machine rejects it); partners and admins → `403`. Cancelled orders
  leave the partner's available pool and stop accepting location updates.
- **JWT**: subject = email, claims carry `userId` + `role`, expired / wrong-key / malformed
  tokens are rejected; the filter rejects them silently (401 on protected routes).
- **Auth**: duplicate email → 409; self-registration as ADMIN → 400; bad credentials → 401;
  validation failures → 400 with `fieldErrors`.
- **Authorization**: `GET /api/restaurants/**` public; writes ADMIN-only; `/api/orders/**`
  authenticated; role-gated endpoints enforce CUSTOMER/DELIVERY_PARTNER/ADMIN (ADMIN can list
  all orders via `GET /api/orders`).
- **Pagination & filtering**: every list endpoint returns a `PageResponse` envelope
  (`content/page/size/totalElements/totalPages/last`); `page`/`size` are Bean-Validated
  (`400` + `fieldErrors`), `sortBy`/`direction` are whitelist-validated (`400` with a clear
  message), filters are composed as `Specification`s, and order lists stay scoped to the
  caller (CUSTOMER→own, DELIVERY_PARTNER→assigned, ADMIN→all). Design rationale, Page vs
  Slice, offset vs cursor pagination and the generated SQL
  are documented in `PAGINATION.md`.

### Estimated coverage (measured by JaCoCo after running)

- `Order.transitionTo` / transition map: ~100%
- `OrderService.acceptOrder` + `updateOrderStatus`: ~90–100% (all branches covered)
- `JwtService`: ~95%
- `AuthService`: ~90%
- `RestaurantService`: ~90%
- `GlobalExceptionHandler`: most mappings (the "happy-path" `Exception → 500` branch is not
  triggered on purpose)
- Whole project: typically **60–70% line coverage**, concentrated on the domain/business code
  above. JPA repositories, DTOs and generated Lombok code contribute little to that number.

## 8. How to run

Prerequisites: Java 21 + Maven. Integration tests additionally need Docker **or** a local MySQL 8.

```bash
# full suite (unit + integration)
mvn test

# unit tests only (no DB needed)
mvn test -Dtest='OrderStateTransitionTest,OrderServiceAcceptanceTest,OptimisticLockingTest,JwtServiceTest'

# integration tests only (Docker or local MySQL required)
mvn test -Dtest='AuthFlowIntegrationTest,OrderFlowIntegrationTest,OrderCancellationIntegrationTest,RestaurantCrudIntegrationTest,GlobalExceptionHandlingIntegrationTest,OrderConcurrencyTest,OpenApiDocumentationIntegrationTest,PaginationFilteringIntegrationTest,LocationTrackingIntegrationTest'

# one class
mvn test -Dtest=OrderFlowIntegrationTest
```

Notes:

- With Docker running, integration tests use Testcontainers automatically (image pulled on first
  run). Without Docker they fall back to local MySQL — start it, e.g.
  `brew services start mysql`, and if your root credentials differ export
  `TEST_DB_URL`, `TEST_DB_USER`, `TEST_DB_PASSWORD`.
- Without any database the integration classes are skipped and only the unit tests run.

## 9. Coverage report

JaCoCo is already wired into `pom.xml`; after `mvn test` open:

```
target/site/jacoco/index.html
```

(`jacoco-maven-plugin` `prepare-agent` instruments the run; `report` writes the HTML/XML/CSV
output at the end of the `test` phase.) For a quick text summary:

```bash
grep -E 'Total|Order|Auth|Restaurant' target/site/jacoco/jacoco.csv | head
```
