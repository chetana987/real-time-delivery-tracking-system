package com.deliverytracking.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the estimated time of arrival (ETA) feature.
 *
 * <p>ETA is a portfolio-grade estimate only: {@code minutes = distanceKm / speed * 60},
 * straight-line distance we already calculate, an assumed average delivery speed, and
 * no routing/traffic data. The speed must be a finite value greater than zero so the
 * estimate is always meaningful; an invalid value fails fast at startup.</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.eta")
public class EtaProperties {

    /** Assumed average delivery speed in km/h used as the ETA divisor. */
    private double averageSpeedKmh;

    @PostConstruct
    void validate() {
        if (!Double.isFinite(averageSpeedKmh) || averageSpeedKmh <= 0) {
            throw new IllegalStateException(
                    "app.eta.average-speed-kmh must be a finite number greater than 0, but was "
                            + averageSpeedKmh
                            + ". Set ETA_AVERAGE_SPEED_KMH to a positive value (e.g. 30).");
        }
    }
}