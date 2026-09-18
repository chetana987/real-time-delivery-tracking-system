package com.deliverytracking.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EtaPropertiesTest {

    private EtaProperties properties(double speed) {
        EtaProperties props = new EtaProperties();
        props.setAverageSpeedKmh(speed);
        return props;
    }

    @Test
    void validSpeed_passesValidation() {
        assertThatCode(() -> properties(30).validate()).doesNotThrowAnyException();
        assertThatCode(() -> properties(1).validate()).doesNotThrowAnyException();
    }

    @Test
    void zeroSpeed_failsFast() {
        assertThatThrownBy(() -> properties(0).validate())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void negativeSpeed_failsFast() {
        assertThatThrownBy(() -> properties(-30).validate())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void nonFiniteSpeed_failsFast() {
        assertThatThrownBy(() -> properties(Double.NaN).validate())
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> properties(Double.POSITIVE_INFINITY).validate())
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> properties(Double.NEGATIVE_INFINITY).validate())
                .isInstanceOf(IllegalStateException.class);
    }
}