package com.deliverytracking.dto;

import com.deliverytracking.entity.Restaurant;
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
@Schema(description = "A restaurant")
public class RestaurantResponse {

    @Schema(description = "Restaurant id", example = "1")
    private Long id;
    @Schema(description = "Restaurant name", example = "Spice Garden")
    private String name;
    @Schema(description = "Restaurant street address", example = "12 MG Road, Bangalore")
    private String address;
    @Schema(description = "Latitude of the restaurant", example = "12.9716")
    private Double lat;
    @Schema(description = "Longitude of the restaurant", example = "77.5946")
    private Double lng;

    public static RestaurantResponse from(Restaurant restaurant) {
        return RestaurantResponse.builder()
                .id(restaurant.getId())
                .name(restaurant.getName())
                .address(restaurant.getAddress())
                .lat(restaurant.getLat())
                .lng(restaurant.getLng())
                .build();
    }
}
