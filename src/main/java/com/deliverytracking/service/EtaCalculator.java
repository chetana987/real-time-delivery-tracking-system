package com.deliverytracking.service;

/**
 * Simple, honest estimated time of arrival based on an existing geographic
 * distance and a configurable average delivery speed:
 *
 * <pre>{@code minutes = distanceKm / averageSpeedKmh * 60}</pre>
 *
 * <p>This is a portfolio-grade estimate, not a road-routing or traffic-aware
 * prediction. It rounds to a whole minute and never fabricates an ETA: a missing
 * or invalid distance, or a non-positive/non-finite speed, yields {@code null}
 * rather than a meaningless {@code 0 min}.</p>
 */
public final class EtaCalculator {

    private static final double MINUTES_PER_HOUR = 60.0;

    private EtaCalculator() {
    }

    /**
     * Estimated arrival time in whole minutes.
     *
     * @param distanceKm      great-circle distance to the destination, or null
     * @param averageSpeedKmh assumed average delivery speed
     * @return whole minutes, or {@code null} when the distance is missing/negative/
     *         non-finite or the speed is non-positive/non-finite
     */
    public static Integer etaMinutes(Double distanceKm, double averageSpeedKmh) {
        if (distanceKm == null || !Double.isFinite(distanceKm) || distanceKm < 0) {
            return null;
        }
        if (!Double.isFinite(averageSpeedKmh) || averageSpeedKmh <= 0) {
            return null;
        }
        double minutes = distanceKm / averageSpeedKmh * MINUTES_PER_HOUR;
        return (int) Math.round(minutes);
    }
}