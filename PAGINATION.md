# Pagination, Sorting and Filtering — Delivery Tracking System

Every list endpoint now returns a page envelope instead of a raw array. This document
explains why, how it works, and what SQL Spring Data generates under the hood.

---

## 1. Why pagination is necessary

A list endpoint that returns *everything* has three problems:

1. **Memory and payload size.** An order history or "available orders" feed grows without
   bound. Returning 100 000 rows in one response means the JVM materialises 100 000 entity
   objects and the client parses a multi-megabyte JSON body for a screen that can show ~10 rows.
2. **Latency.** The DB has to serialise and ship every row; the client has to wait for the
   whole response before it can render the first item.
3. **Stability.** A growing table makes the endpoint progressively slower. With pagination the
   cost of a single request is bounded by the page size plus a cheap `COUNT(*)`, so response
   time stays flat no matter how much history accumulates.

Pagination also **bounds the result set in one round trip** — the server sends only the page
the client asked for, plus the metadata needed to build "next / last" navigation links.

## 2. Page vs Slice — what Spring Data returns

Both come from Spring Data and both carry `content` plus `page`/`size`:

| | `Page<T>` | `Slice<T>` |
|---|---|---|
| Loads the requested page | ✅ | ✅ |
| Runs a `COUNT(*)` for `totalElements`/`totalPages` | ✅ | ❌ |
| Extra cost per request | One more query | None |
| `hasNext()` support | ✅ (via total) | ✅ (fetches `size + 1` rows) |
| Good for | Screens that show "1–10 of 125" or pager controls | Infinite scroll / "load more" |

`Page` gives you **totals** (used by this API) at the cost of a second `COUNT` query.
`Slice` avoids the count but only tells you whether there *is* a next page (it reads one extra
row and discards it). This API deliberately uses `Page`: the requirement says to return
`totalElements` / `totalPages` / `last`, which only `Page` can provide.

The wire format is our own small envelope (`PageResponse`) rather than Spring's default
`PageImpl` serialization, so the JSON is stable, minimal and independent of Spring internals:

```json
{
  "content": [],
  "page": 0,
  "size": 10,
  "totalElements": 125,
  "totalPages": 13,
  "last": false
}
```

## 3. Offset pagination vs cursor pagination

**Offset (this API).** `LIMIT {size} OFFSET {page * size}`:

```sql
SELECT * FROM orders ORDER BY created_at DESC LIMIT 10 OFFSET 40;   -- page 4, size 10
```

- Simple; the client can jump to *any* page (`?page=7`) with no previous request.
- Two queries per request: one `COUNT`, one `SELECT`.
- `OFFSET` must scan and discard the first `offset` rows every time — deep pages get
  progressively more expensive.

**Cursor (keyset).** No page numbers; the client passes the sort key of the last item seen:

```sql
WHERE (created_at, id) < ('2026-08-05 10:00:00', 4123) ORDER BY created_at DESC, id DESC LIMIT 10;
```

- Each request starts exactly where the previous one stopped — the DB walks the index
  directly instead of skipping `offset` rows, so all pages cost about the same.
- No `COUNT` is needed for `hasNext` (fetch `size + 1`), so it is much cheaper.
- The client can only move *forward*; there is no stable "jump to page 7".

## 4. When cursor pagination becomes preferable

- **Very large tables** (millions of rows): `OFFSET 1_000_000` forces the server to walk a
  million rows; a keyset query jumps straight to the relevant index position.
- **Live feeds** (our "available orders" screen): new rows arrive at the top while the user
  scrolls. With offset paging, inserting rows shifts every page and items can be skipped or
  duplicated; keyset paging is immune because it anchors on the last seen item.
- **Real-time tracking UIs** (location history streamed while a delivery is in progress).
- In exchange you lose random page access and totals (keyset is almost always `Slice`-like).

**Our recommendation:** keep offset pagination for `GET /api/restaurants` and
`GET /api/restaurants/{id}/menu` (bounded, admin-maintained datasets) and for
`GET /api/orders` (per-user scoping keeps each list small). If `GET /api/orders/available` or
the location-history feed ever grows large enough to matter, those two are the natural
candidates to migrate to keyset pagination.

## 5. Performance considerations

- **Count + select.** A `Page` costs two statements. The `COUNT` on a filtered query still
  scans the matching rows unless it can be served by an index; on our small-per-customer
  scopes this is negligible.
- **Sorting is only cheap if the index covers it.** Sorting 10 rows is nothing; the real risk
  is the `COUNT` and the `OFFSET` skip on deep pages.
- **Don't allow arbitrary `sortBy`.** A whitelist per endpoint (`PagingSupport`) prevents both
  abuse (client ordering by un-indexed/unmapped columns) and obscure
  "Property `x` does not exist" failures. Unknown fields and invalid directions return
  `400 Bad Request`.
- **N+1 lazy loading.** `Page.map(...)` maps entities to DTOs inside the read-only transaction;
  each order touches its lazy `restaurant`/`customer`. For a delivery system this is fine per
  page (≤ 100 rows), but for large pages a `JOIN FETCH` or a projection DTO avoids it.
- **`size` is capped** (`@Max(100)`) and `page` is validated (`@Min(0)`), so a client cannot
  force a huge page or a negative offset.
- **String filters use case-insensitive `LIKE '%…%'`** — these cannot use a b-tree index (the
  wildcard is on the left). They are fine for small tables; a prefix search (`LIKE 'x%'`) or
  full-text index would be the optimisation path if menus grow.

## 6. Database indexes supporting pagination

- **`ORDER BY` + `OFFSET`:** an index on the sort columns lets MySQL return rows in index
  order without a filesort. Default sort for orders is `created_at DESC`, which is covered by
  a `(created_at)` or `(customer_id, created_at)` index.
- **Filter + sort together:** the most useful indexes are *composite*, leading with the
  equality filter column then the sort column:
  - `orders (customer_id, created_at)` — "my orders, newest first" (the main screen).
  - `orders (delivery_partner_id, status)` — already present in `Order`.
  - `orders (status, created_at)` — "available orders" feed (`status = 'PLACED'` + recency).
  - `menu_items (restaurant_id)` — already present; extend to
    `(restaurant_id, price)` if price-range filters become hot.
- **Cursor pagination** needs a unique tiebreaker after the sort key (e.g. `id`) so the
  keyset predicate is unambiguous; without one, rows with equal sort values can be missed or
  duplicated across pages.

The current schema already declares `idx_orders_customer`, `idx_orders_partner`,
`idx_orders_partner_status` and `idx_menu_items_restaurant` (`@Index` on the entities).
A future migration could add `idx_orders_status_created` and `idx_orders_customer_created`
for the two most-used list queries.

## 7. Modified endpoints

| Endpoint | Now returns | Pagination | Sorting (`sortBy`) | Filters |
|---|---|---|---|---|
| `GET /api/orders` | `PageResponse<OrderResponse>` | ✅ | id, status, totalAmount, createdAt, updatedAt | status, customer, deliveryPartner, restaurant, from, to |
| `GET /api/orders/available` | `PageResponse<OrderResponse>` | ✅ | id, status, totalAmount, createdAt, updatedAt | restaurant |
| `GET /api/restaurants` | `PageResponse<RestaurantResponse>` | ✅ | id, name, address, lat, lng | name |
| `GET /api/restaurants/{id}/menu` | `PageResponse<MenuItemResponse>` | ✅ | id, name, price | itemName, minPrice, maxPrice |
| `GET /api/orders/{orderId}/location/history` | `PageResponse<LocationUpdateMessage>` | ✅ | id, timestamp | — |

Notes:

- **`city` filter:** the `Restaurant` entity has a free-text `address` but no structured
  `city` field, so a city filter is **not implemented** (a reliable parser for free-text
  addresses is out of scope). The `name` filter covers the practical search case; adding a
  real `city` column + index later would plug straight into the same `Specification` pattern.
- **`GET /api/orders` stays role-scoped.** A CUSTOMER only ever sees their own orders, a
  DELIVERY_PARTNER only their assigned ones, and an ADMIN sees all orders. The
  `customer`/`deliveryPartner` filters narrow within that scope and never widen it.
- Sort fields are validated against the per-endpoint whitelist (`400` on unknown field or
  invalid direction). `page`/`size` are Bean-Validated (`400` with `fieldErrors`).
- The pagination envelope replaces the previous raw-array bodies — this is the intended
  contract change. `PageResponse.from(Page)` is used in every service.

## 8. Example requests

```text
# Restaurants: page 2, 10 per page, sorted by name desc, filtered by name containing "spice"
GET /api/restaurants?page=1&size=10&sortBy=name&direction=desc&name=spice

# Menu: items priced between 5 and 15, sorted by price asc
GET /api/restaurants/1/menu?sortBy=price&direction=asc&minPrice=5&maxPrice=15

# Menu: item name filter
GET /api/restaurants/1/menu?itemName=burger

# My orders: DELIVERED orders of customer 2 on 2026-08-01..2026-08-05, newest first
GET /api/orders?status=DELIVERED&customer=2&from=2026-08-01&to=2026-08-05
Authorization: Bearer <jwt>

# Available orders (partner feed): newest PLACED orders first, 10 per page
GET /api/orders/available
Authorization: Bearer <jwt>
```

## 9. Example responses

```json
// GET /api/restaurants?page=0&size=10&sortBy=name&direction=asc
{
  "content": [
    {
      "id": 7,
      "name": "Spice Garden",
      "address": "12 MG Road, Bangalore",
      "lat": 12.9716,
      "lng": 77.5946
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 125,
  "totalPages": 13,
  "last": false
}
```

```json
// GET /api/orders?status=DELIVERED&page=1&size=2
{
  "content": [
    {
      "id": 18,
      "customerId": 2,
      "deliveryPartnerId": 3,
      "restaurantId": 7,
      "restaurantName": "Spice Garden",
      "status": "DELIVERED",
      "totalAmount": 19.98,
      "deliveryAddress": "221B Baker Street, London",
      "createdAt": "2026-08-05T10:00:00Z",
      "updatedAt": "2026-08-05T10:50:00Z"
    }
  ],
  "page": 1,
  "size": 2,
  "totalElements": 5,
  "totalPages": 3,
  "last": false
}
```

## 10. SQL generated by Spring Data (conceptually)

Spring Data builds the SQL from the `Specification` + `Pageable`. For
`GET /api/orders?status=DELIVERED&customer=2&page=0&size=10&sortBy=createdAt&direction=desc`
(two statements — one count, one page):

```sql
-- 1) count over the filtered rows
select count(o.id)
from orders o
where o.status = 'DELIVERED'
  and o.customer_id = 2;

-- 2) the page itself, sorted and offset
select o.*
from orders o
where o.status = 'DELIVERED'
  and o.customer_id = 2
order by o.created_at desc
limit 10 offset 0;
```

For `GET /api/restaurants?name=spice&sortBy=name&direction=asc&page=1&size=10`:

```sql
select count(r.id) from restaurants r
where lower(r.name) like '%spice%';

select r.* from restaurants r
where lower(r.name) like '%spice%'
order by r.name asc
limit 10 offset 10;
```

For `GET /api/restaurants/1/menu?minPrice=5&maxPrice=15&sortBy=price&direction=asc`:

```sql
select count(m.id) from menu_items m
where m.restaurant_id = 1
  and m.price >= 5
  and m.price <= 15;

select m.* from menu_items m
where m.restaurant_id = 1
  and m.price >= 5
  and m.price <= 15
order by m.price asc
limit 10 offset 0;
```

Because the filters are built as `Specification` fragments (`OrderSpecs`, `RestaurantSpecs`,
`MenuItemSpecs`), each combination is a normal, index-friendly `WHERE` — the same method
`findAll(Specification, Pageable)` is used everywhere, so the pattern is identical for every
list endpoint.
