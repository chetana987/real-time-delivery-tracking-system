package com.deliverytracking.service;

/**
 * Great-circle distance between geographic coordinates using the Haversine
 * formula. Distances are great-circle (straight-line on a sphere), never
 * road/route distance, and are returned in kilometres.
 *
 * <p>Two entry points with different null/invalid handling:</p>
 * <ul>
 *   <li>{@link #haversineKm(double, double, double, double)} is the strict
 *       calculation and rejects coordinates outside the valid ranges
 *       (lat ∈ [-90, 90], lng ∈ [-180, 180]) with an
 *       {@link IllegalArgumentException}.</li>
 *   <li>{@link #distanceKmOrNull(Double, Double, Double, Double)} is the safe
 *       facade for data read back from storage: null or out-of-range inputs
 *       yield {@code null} instead of a fabricated {@code 0 km}.</li>
 * </ul>
 */
public final class GeoDistance {

    /**
     * Mean Earth radius in kilometres, matching the WGS-84 semi-axes average
     * (≈ 6371.0088 km).
     */
    private static final double EARTH_RADIUS_KM = 6371.0088;

    private static final double MAX_LATITUDE = 90.0;
    private static final double MAX_LONGITUDE = 180.0;

    private GeoDistance() {
    }

    /**
     * Distance in kilometres between two points using the Haversine formula.
     *
     * @throws IllegalArgumentException if either coordinate pair is outside the
     *                                  valid ranges or contains NaN/infinity
     */
    public static double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        if (!isValid(lat1, lng1) || !isValid(lat2, lng2)) {
            throw new IllegalArgumentException("Coordinates must be valid: "
                    + "latitude in [-90, 90], longitude in [-180, 180]");
        }

        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double sinLat = Math.sin(dLat / 2.0);
        double sinLng = Math.sin(dLng / 2.0);
        double a = sinLat * sinLat
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * sinLng * sinLng;
        return 2.0 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(a));
    }

    /**
     * Safe facade for coordinates loaded from storage. Returns the haversine
     * distance in km, or {@code null} if any coordinate is missing or invalid —
     * the caller can safely treat {@code null} as "distance unknown" without
     * ever showing a meaningless {@code 0 km}.
     */
    public static Double distanceKmOrNull(Double lat1, Double lng1, Double lat2, Double lng2) {
        if (lat1 == null || lng1 == null || lat2 == null || lng2 == null) {
            return null;
        }
        if (!isValid(lat1, lng1) || !isValid(lat2, lng2)) {
            return null;
        }
        return haversineKm(lat1, lng1, lat2, lng2);
    }

    private static boolean isValid(double lat, double lng) {
        // NaN also fails these comparisons, so NaN is naturally rejected.
        return lat >= -MAX_LATITUDE && lat <= MAX_LATITUDE
                && lng >= -MAX_LONGITUDE && lng <= MAX_LONGITUDE;
    }
}