/**
 * Demo-only simulated GPS movement for the delivery partner dashboard.
 *
 * There is no real phone GPS in this project, so the partner can play back a
 * fake ride HEADING TOWARD THE ORDER'S REAL DELIVERY DESTINATION.
 *
 * This is intentionally separated from real tracking data:
 *  - real data = the order's stored restaurant/destination coordinates and the
 *    location updates flowing over WebSocket/STOMP;
 *  - demo data  = the interpolated points produced here, which only the partner
 *    can start explicitly ("Start simulated movement"). The simulation NEVER
 *    writes to the order's destination — it only publishes STOMP updates.
 */

/**
 * Returns an L-shaped path (drive east first, then north) from the starting
 * point toward the actual destination, as a list of { lat, lng } points.
 */
export function buildDemoRoute(startLat, startLng, destLat, destLng, steps = 8) {
  const points = [];
  for (let i = 0; i <= steps; i += 1) {
    const t = i / steps;
    // First 60% of the trip is the eastward leg, last 40% is the northward leg.
    const leg1 = Math.min(t / 0.6, 1);
    const leg2 = Math.max((t - 0.6) / 0.4, 0);
    points.push({
      lat: startLat + leg2 * (destLat - startLat),
      lng: startLng + leg1 * (destLng - startLng),
    });
  }
  return points;
}