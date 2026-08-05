package com.deliverytracking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
@Schema(description = "Payload to create or update a restaurant (ADMIN only)")
public class RestaurantRequest {

    @NotBlank
    @Size(max = 150)
    @Schema(description = "Restaurant name", example = "Spice Garden")
    private String name;

    @NotBlank
    @Schema(description = "Restaurant street address", example = "12 MG Road, Bangalore")
    private String address;

    @NotNull
    @Schema(description = "Latitude of the restaurant", example = "12.9716")
    private Double lat;

    @NotNull
    @Schema(description = "Longitude of the restaurant", example = "77.5946")
    private Double lng;
}
