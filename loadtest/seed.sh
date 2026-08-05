#!/usr/bin/env bash
# Seeds the users/orders needed by the load tests through the public API and
# prints the POOL values to pass to the k6 scripts.
#
# Prerequisites: stack running (docker compose up -d), loadtest/seed.sql already
# applied (or this script will apply it), curl + jq installed.
#
# Usage:
#   ./loadtest/seed.sh                     # defaults: 20 partners, 10 race partners
#   PARTNERS=50 RACE_PARTNERS=20 ./loadtest/seed.sh
set -euo pipefail

if [ -f .env ]; then set -a; . ./.env; set +a; fi

BASE_URL="${BASE_URL:-http://localhost:8080}"
PASSWORD="${PASSWORD:-Loadtest1!}"
PARTNERS="${PARTNERS:-20}"
RACE_PARTNERS="${RACE_PARTNERS:-10}"
RESTAURANT_ID="${RESTAURANT_ID:-}"
MENU_ITEM_ID="${MENU_ITEM_ID:-}"
DOMAIN="${DOMAIN:-loadtest.local}"
CUSTOMER_EMAIL="${CUSTOMER_EMAIL:-customer1@$DOMAIN}"

register() {
  local email="$1" role="$2" body
  body=$(jq -nc --arg name "$email" --arg email "$email" --arg password "$PASSWORD" --arg role "$role" \
    '{name:$name,email:$email,password:$password,role:$role}')
  local code
  code=$(curl -sS -o /tmp/reg.json -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -d "$body" "$BASE_URL/api/auth/register")
  if [ "$code" = "201" ]; then
    jq -r '.token' /tmp/reg.json
    return 0
  fi
  code=$(curl -sS -o /tmp/login.json -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -d "{\"email\":\"$email\",\"password\":\"$PASSWORD\"}" "$BASE_URL/api/auth/login")
  if [ "$code" != "200" ]; then
    echo "register/login failed for $email (register=$code login=$code)" >&2
    return 1
  fi
  jq -r '.token' /tmp/login.json
}

restaurant_id() {
  if [ -n "$RESTAURANT_ID" ]; then echo "$RESTAURANT_ID"; return; fi
  curl -sS "$BASE_URL/api/restaurants" | jq -r '.[0].id // empty'
}

menu_item_id() {
  local rid="$1"
  if [ -n "$MENU_ITEM_ID" ]; then echo "$MENU_ITEM_ID"; return; fi
  curl -sS "$BASE_URL/api/restaurants/$rid/menu" | jq -r '.[0].id // empty'
}

place_order() {
  local token="$1" rid="$2" mid="$3" body
  body=$(jq -nc --argjson restaurantId "$rid" --argjson menuItemId "$mid" \
    '{restaurantId:$restaurantId,deliveryAddress:"Load Test Address, Bengaluru",items:[{menuItemId:$menuItemId,quantity:1}]}')
  curl -sS -X POST -H 'Content-Type: application/json' -H "Authorization: Bearer $token" \
    -d "$body" "$BASE_URL/api/orders" | jq -r '.id'
}

accept_order() {
  local token="$1" oid="$2"
  curl -sS -X PATCH -H 'Content-Type: application/json' -H "Authorization: Bearer $token" \
    "$BASE_URL/api/orders/$oid/accept" >/dev/null
}

RID=$(restaurant_id)
if [ -z "$RID" ]; then
  echo "No restaurant found; applying loadtest/seed.sql via docker compose ..." >&2
  docker compose exec -T mysql mysql -uroot -p"${MYSQL_ROOT_PASSWORD:-delivery-root-dev}" \
    "${MYSQL_DB:-delivery_tracking}" < loadtest/seed.sql
  RID=$(restaurant_id)
fi
if [ -z "$RID" ]; then
  echo "Could not obtain a restaurant id. Is the backend up?" >&2
  exit 1
fi
MID=$(menu_item_id "$RID")
if [ -z "$MID" ]; then
  echo "Restaurant $RID has no menu items." >&2
  exit 1
fi
echo "Using restaurant=$RID menuItem=$MID" >&2

CUSTOMER_TOKEN=$(register "$CUSTOMER_EMAIL" "CUSTOMER")
echo "Customer ready: $CUSTOMER_EMAIL" >&2

STREAM_POOL=""
FIRST_ORDER=""
for i in $(seq 1 "$PARTNERS"); do
  email="partner$i@$DOMAIN"
  token=$(register "$email" "DELIVERY_PARTNER")
  oid=$(place_order "$CUSTOMER_TOKEN" "$RID" "$MID")
  accept_order "$token" "$oid"
  if [ -z "$FIRST_ORDER" ]; then FIRST_ORDER="$oid"; fi
  if [ -n "$STREAM_POOL" ]; then STREAM_POOL="$STREAM_POOL;"; fi
  STREAM_POOL="$STREAM_POOL$email:$PASSWORD:$oid"
  if [ $((i % 10)) -eq 0 ]; then echo "seeded $i/$PARTNERS partners" >&2; fi
done

RACE_POOL=""
for i in $(seq 1 "$RACE_PARTNERS"); do
  email="race$i@$DOMAIN"
  register "$email" "DELIVERY_PARTNER" >/dev/null
  if [ -n "$RACE_POOL" ]; then RACE_POOL="$RACE_POOL;"; fi
  RACE_POOL="$RACE_POOL$email:$PASSWORD:"
done

RACE_ORDER=$(place_order "$CUSTOMER_TOKEN" "$RID" "$MID")

echo
echo "=================== copy-paste for k6 ==================="
echo "STREAM_POOL=$STREAM_POOL"
echo "E2E: EMAIL=partner1@$DOMAIN CUSTOMER_EMAIL=$CUSTOMER_EMAIL ORDER_ID=$FIRST_ORDER"
echo "RACE_ORDER_ID=$RACE_ORDER"
echo "RACE_POOL=$RACE_POOL"
echo "=========================================================="
