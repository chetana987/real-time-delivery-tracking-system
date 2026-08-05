-- Seed data that cannot be created through the public API.
-- Restaurants and menu items are ADMIN-only (self-registration as ADMIN is
-- blocked), so they are inserted here. Users and orders are created through
-- the API by seed.sh.
--
-- Run (from the project root, with the stack up):
--   docker compose exec -T mysql mysql -uroot -pdelivery-root-dev delivery_tracking < loadtest/seed.sql
-- (or use the app user: -udelivery -pdelivery-dev)

INSERT INTO restaurants (name, address, lat, lng) VALUES
  ('Load Test Kitchen', '1 MG Road, Bengaluru', 12.9716, 77.5946);

-- LAST_INSERT_ID() returns the auto-increment id of the restaurant above on
-- the same connection, so the menu items reference it without hardcoding ids.
INSERT INTO menu_items (restaurant_id, name, price) VALUES
  (LAST_INSERT_ID(), 'Test Burger', 199.00),
  (LAST_INSERT_ID(), 'Test Pizza', 299.00),
  (LAST_INSERT_ID(), 'Test Coffee',  99.00);
