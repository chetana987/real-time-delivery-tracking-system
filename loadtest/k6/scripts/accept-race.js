// accept-race.js
//
// Fires RACE_PARTNERS concurrent PATCH /api/orders/{id}/accept requests at a
// single PLACED order. The orders table uses optimistic locking (@Version on
// Order + OrderService.acceptOrder), so exactly ONE partner must win and every
// other request must receive HTTP 409 (OptimisticLockConflictException).
//
// All partners are logged in once, up front, in setup() so the accept PATCHes
// start at (almost) the same instant instead of being staggered by login time.
//
// The target order MUST be in PLACED state with no partner assigned before the
// run. Re-running on the same order fails the `race_wins` threshold by design
// (it is already ACCEPTED), so re-seed the order between runs.
import http from 'k6/http';
import { Counter } from 'k6/metrics';
import { BASE, ORDER_ID, RACE_PARTNERS, poolEntry } from '../lib/config.js';
import { login } from '../lib/login.js';

const raceWins = new Counter('race_wins');
const raceConflicts = new Counter('race_conflicts');
const raceErrors = new Counter('race_errors');

export const options = {
  vus: RACE_PARTNERS,
  iterations: RACE_PARTNERS,
  thresholds: {
    race_wins: ['count==1'],
    race_conflicts: [`count>=${RACE_PARTNERS - 1}`],
  },
};

export function setup() {
  const tokens = [];
  for (let i = 0; i < RACE_PARTNERS; i += 1) {
    const creds = poolEntry(i);
    tokens.push({ email: creds.email, token: login(BASE, creds.email, creds.password) });
  }
  return tokens;
}

export default function (data) {
  const token = data[__VU - 1].token;
  if (!token) {
    raceErrors.add(1);
    return;
  }

  const res = http.request(
    'PATCH',
    `${BASE}/api/orders/${ORDER_ID}/accept`,
    null,
    { headers: { Authorization: `Bearer ${token}` } },
  );

  if (res.status === 200) {
    raceWins.add(1);
  } else if (res.status === 409) {
    raceConflicts.add(1);
  } else {
    raceErrors.add(1);
  }
}
