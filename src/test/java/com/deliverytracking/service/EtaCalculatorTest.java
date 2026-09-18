package com.deliverytracking.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EtaCalculatorTest {

    @Test
    void zeroDistance_returnsZeroMinutes() {
        assertThat(EtaCalculator.etaMinutes(0.0, 30)).isZero();
    }

    @Test
    void fiveKmAt30Kmh_returns10Minutes() {
        assertThat(EtaCalculator.etaMinutes(5.0, 30)).isEqualTo(10);
    }

    @Test
    void tenKmAt30Kmh_returns20Minutes() {
        assertThat(EtaCalculator.etaMinutes(10.0, 30)).isEqualTo(20);
    }

    @Test
    void fractionalDistance_roundsToNearestWholeMinute() {
        // 4.25 km at 30 km/h = 8.5 minutes -> rounds half-up to 9.
        assertThat(EtaCalculator.etaMinutes(4.25, 30)).isEqualTo(9);
        // 1.067 km at 30 km/h = 2.134 minutes -> 2.
        assertThat(EtaCalculator.etaMinutes(1.067, 30)).isEqualTo(2);
    }

    @Test
    void largerDistance_roundsToWholeMinute() {
        // 8.879 km at 30 km/h = 17.76 minutes -> 18.
        assertThat(EtaCalculator.etaMinutes(8.879, 30)).isEqualTo(18);
    }

    @Test
    void speedIsConfigurable() {
        // Same distance at a higher average speed halves the ETA.
        assertThat(EtaCalculator.etaMinutes(10.0, 60)).isEqualTo(10);
        assertThat(EtaCalculator.etaMinutes(10.0, 20)).isEqualTo(30);
    }

    @Test
    void missingDistance_returnsNullNotZero() {
        assertThat(EtaCalculator.etaMinutes(null, 30)).isNull();
    }

    @Test
    void negativeDistance_returnsNull() {
        assertThat(EtaCalculator.etaMinutes(-1.0, 30)).isNull();
    }

    @Test
    void nonFiniteDistance_returnsNull() {
        assertThat(EtaCalculator.etaMinutes(Double.NaN, 30)).isNull();
        assertThat(EtaCalculator.etaMinutes(Double.POSITIVE_INFINITY, 30)).isNull();
        assertThat(EtaCalculator.etaMinutes(Double.NEGATIVE_INFINITY, 30)).isNull();
    }

    @Test
    void zeroSpeed_returnsNull() {
        assertThat(EtaCalculator.etaMinutes(5.0, 0)).isNull();
    }

    @Test
    void negativeSpeed_returnsNull() {
        assertThat(EtaCalculator.etaMinutes(5.0, -30)).isNull();
    }

    @Test
    void nonFiniteSpeed_returnsNull() {
        assertThat(EtaCalculator.etaMinutes(5.0, Double.NaN)).isNull();
        assertThat(EtaCalculator.etaMinutes(5.0, Double.POSITIVE_INFINITY)).isNull();
        assertThat(EtaCalculator.etaMinutes(5.0, Double.NEGATIVE_INFINITY)).isNull();
    }
}