package com.deliverytracking.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;

class GeoDistanceTest {

    @Test
    void identicalCoordinates_returnZero() {
        assertThat(GeoDistance.haversineKm(18.5204, 73.8567, 18.5204, 73.8567)).isZero();
        assertThat(GeoDistance.haversineKm(-90.0, -180.0, -90.0, -180.0)).isZero();
        assertThat(GeoDistance.distanceKmOrNull(0.0, 0.0, 0.0, 0.0)).isZero();
    }

    @Test
    void knownCoordinatePair_matchesReferenceDistance() {
        // London → New York is a widely referenced great-circle distance (~5570 km).
        assertThat(GeoDistance.haversineKm(51.5074, -0.1278, 40.7128, -74.0060))
                .isCloseTo(5570.23, offset(2.0));
    }

    @Test
    void shortDistance_isAccurate() {
        // 0.001° of latitude at the equator ≈ 111.195 m.
        assertThat(GeoDistance.haversineKm(18.5204, 73.8567, 18.5214, 73.8567))
                .isCloseTo(0.11120, offset(0.01));
    }

    @Test
    void largerDistance_isAccurate() {
        // Quarter of the Earth's circumference, pole to pole ≈ 10 007.56 km.
        assertThat(GeoDistance.haversineKm(0.0, 0.0, 90.0, 0.0))
                .isCloseTo(10007.557, offset(2.0));
    }

    @Test
    void oneDegreeOfLongitudeAtEquator_isAbout111Km() {
        assertThat(GeoDistance.haversineKm(0.0, 0.0, 0.0, 1.0))
                .isCloseTo(111.195, offset(0.5));
    }

    @Test
    void nullCoordinates_returnNullNotZero() {
        assertThat(GeoDistance.distanceKmOrNull(null, 73.8567, 18.5204, 73.8567)).isNull();
        assertThat(GeoDistance.distanceKmOrNull(18.5204, null, 18.5204, 73.8567)).isNull();
        assertThat(GeoDistance.distanceKmOrNull(18.6000, 73.8500, null, 73.8567)).isNull();
        assertThat(GeoDistance.distanceKmOrNull(18.6000, 73.8500, 18.5204, null)).isNull();
        assertThat(GeoDistance.distanceKmOrNull(null, null, null, null)).isNull();
    }

    @Test
    void invalidLatitude_returnsNullSafely() {
        assertThat(GeoDistance.distanceKmOrNull(95.0, 73.8567, 18.5204, 73.8567)).isNull();
        assertThat(GeoDistance.distanceKmOrNull(18.5204, 73.8567, -91.0, 73.8567)).isNull();
        assertThat(GeoDistance.distanceKmOrNull(Double.NaN, 73.8567, 18.5204, 73.8567)).isNull();
    }

    @Test
    void invalidLongitude_returnsNullSafely() {
        assertThat(GeoDistance.distanceKmOrNull(18.5204, 181.0, 18.5204, 73.8567)).isNull();
        assertThat(GeoDistance.distanceKmOrNull(18.5204, 73.8567, 18.5204, -181.0)).isNull();
        assertThat(GeoDistance.distanceKmOrNull(18.5204, Double.NaN, 18.5204, 73.8567)).isNull();
    }

    @Test
    void strictCalculation_rejectsInvalidCoordinates() {
        assertThatThrownBy(() -> GeoDistance.haversineKm(95.0, 73.8567, 18.5204, 73.8567))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GeoDistance.haversineKm(18.5204, 73.8567, 0.0, -181.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GeoDistance.haversineKm(Double.NaN, 73.8567, 18.5204, 73.8567))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void boundaryCoordinates_areValid() {
        assertThat(GeoDistance.distanceKmOrNull(-90.0, -180.0, -90.0, -180.0)).isZero();
        assertThat(GeoDistance.distanceKmOrNull(90.0, 180.0, 90.0, 180.0)).isZero();
    }
}