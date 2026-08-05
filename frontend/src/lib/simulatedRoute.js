/**
 * Demo-only simulated GPS route for the delivery partner dashboard.
 * There is no real phone GPS in this project, so the partner can play back a
 * fake ride from the restaurant toward a computed destination.
 */

export function demoDestination(restaurantLat, restaurantLng) {
  // Roughly ~1.4 km north and ~1.8 km east of the restaurant.
  return {
    lat: restaurantLat + 0.0126,
    lng: restaurantLng + 0.018,
  };
}

/**
 * Returns an L-shaped path (drive east first, then north) from the restaurant
 * to the demo destination, as a list of { lat, lng } points.
 */
export function buildDemoRoute(restaurantLat, restaurantLng, steps = 8) {
  const dest = demoDestination(restaurantLat, restaurantLng);
  const points = [];
  for (let i = 0; i <= steps; i += 1) {
    const t = i / steps;
    // First 60% of the trip is the eastward leg, last 40% is the northward leg.
    const leg1 = Math.min(t / 0.6, 1);
    const leg2 = Math.max((t - 0.6) / 0.4, 0);
    points.push({
      lat: restaurantLat + leg2 * (dest.lat - restaurantLat),
      lng: restaurantLng + leg1 * (dest.lng - restaurantLng),
    });
  }
  return points;
}
