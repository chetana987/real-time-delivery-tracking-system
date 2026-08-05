package com.deliverytracking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "A location update published by a delivery partner for an order")
public class LocationUpdateMessage {

    @Schema(description = "Order id", example = "1")
    private Long orderId;
    @Schema(description = "Latitude of the partner", example = "12.9750")
    private Double lat;
    @Schema(description = "Longitude of the partner", example = "77.6050")
    private Double lng;
    @Schema(description = "Delivery partner user id", example = "5")
    private Long deliveryPartnerId;
    @Schema(description = "When the update was recorded (ISO-8601)", example = "2026-08-05T10:20:00Z")
    private Instant timestamp;
}
