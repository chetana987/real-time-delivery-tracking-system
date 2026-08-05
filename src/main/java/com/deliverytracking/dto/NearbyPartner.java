package com.deliverytracking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "A nearby online delivery partner shown on PLACED orders")
public class NearbyPartner {

    @Schema(description = "Delivery partner user id", example = "5")
    private Long deliveryPartnerId;
    @Schema(description = "Straight-line distance in km from the restaurant", example = "2.4")
    private Double distanceKm;
}
